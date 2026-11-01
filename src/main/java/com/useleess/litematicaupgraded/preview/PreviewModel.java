package com.useleess.litematicaupgraded.preview;

import java.nio.IntBuffer;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.WorldVertexBufferUploader;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.renderer.vertex.VertexFormatElement;
import org.lwjgl.opengl.GL11;

/**
 * A preview being built: the textured quads of the shown blocks in pieces (one per section; opaque and cut-out blocks, then translucent ones),
 * made on build threads and handed over with {@link #addPiece} as plain arrays. The render thread turns the finished pieces into display lists a
 * few at a time ({@link #upload}) through one buffer it keeps, and draws what is there. {@link #free()} gives the display lists back.
 * <p>
 * No piece holds a direct buffer of its own: a new direct buffer per section filled the game's direct memory with garbage, and Java 8 then
 * makes the next allocation sleep 100 ms while it holds {@code GLAllocation}'s lock, which the render thread needs to make a display list.
 */
final class PreviewModel {
    private static final class Piece {
        final VertexFormat format;
        final int[] solid, translucent;

        Piece(VertexFormat format, int[] solid, int[] translucent) {
            this.format = format;
            this.solid = solid;
            this.translucent = translucent;
        }
    }

    private static int liveLists;
    /** Render thread: the buffer every piece is drawn into a display list from, grown to the biggest piece and kept. */
    private static BufferBuilder uploadBuffer;

    final long blockCount;
    /** Source blocks per shown block edge (1 = the file as it is). */
    final int step;
    /** False for the rough copy that is shown while the full one is built. */
    final boolean full;
    /** Size and centre of the shown schematic, in shown blocks. */
    final int sizeX, sizeY, sizeZ;
    final float centerX, centerY, centerZ;
    private final int pieceCount;
    private final ConcurrentLinkedQueue<Piece> ready = new ConcurrentLinkedQueue<>();
    private final AtomicInteger built = new AtomicInteger(), vertices = new AtomicInteger(), translucentVertices = new AtomicInteger();
    private volatile boolean textured;
    private final IntArrayList solidLists = new IntArrayList(), translucentLists = new IntArrayList();
    private int uploaded;
    private long uploadNanos;

    PreviewModel(int sizeX, int sizeY, int sizeZ, int step, long blockCount, int pieceCount, boolean full) {
        this.blockCount = blockCount;
        this.pieceCount = pieceCount;
        this.full = full;
        this.step = step;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.centerX = sizeX / 2f;
        this.centerY = sizeY / 2f;
        this.centerZ = sizeZ / 2f;
    }

    /** Build threads: a finished section, copied out of the builders (which the thread then reuses). */
    void addPiece(BufferBuilder solid, BufferBuilder translucent) {
        if (hasTexture(solid)) textured = true;
        vertices.addAndGet(solid.getVertexCount() + translucent.getVertexCount());
        translucentVertices.addAndGet(translucent.getVertexCount());
        ready.add(new Piece(solid.getVertexFormat(), copy(solid), copy(translucent)));
        built.incrementAndGet();
    }

    private static int[] copy(BufferBuilder buffer) {
        IntBuffer data = buffer.getByteBuffer().asIntBuffer();
        int[] copied = new int[buffer.getVertexCount() * buffer.getVertexFormat().getIntegerSize()];
        data.get(copied);
        return copied;
    }

    private static boolean hasTexture(BufferBuilder buffer) {
        for (VertexFormatElement element : buffer.getVertexFormat().getElements()) {
            if (element.getUsage() == VertexFormatElement.EnumUsage.UV && element.getIndex() == 0) return true;
        }
        return false;
    }

    /** How many display lists of preview models exist now (tests: 0 once the screen is closed). */
    static int liveLists() {
        return liveLists;
    }

    /** Vertices of the sections built so far. */
    int vertexCount() {
        return vertices.get();
    }

    /** Vertices of the translucent blocks (stained glass) of the sections built so far. */
    int translucentVertexCount() {
        return translucentVertices.get();
    }

    boolean textured() {
        return textured;
    }

    /** Percent of the sections that are built (0 to 100). */
    int percent() {
        return pieceCount == 0 ? 100 : built.get() * 100 / pieceCount;
    }

    /** Whether every section is built and turned into display lists. */
    boolean complete() {
        return uploaded == pieceCount;
    }

    /** Sections that are drawn now. */
    int uploadedPieces() {
        return uploaded;
    }

    long uploadMillis() {
        return uploadNanos / 1_000_000;
    }

    /**
     * Render thread only: turns finished sections into display lists until the deadline ({@link System#nanoTime()}), at least one when
     * one is ready, so it always moves on.
     */
    void upload(long deadlineNanos) {
        long started = System.nanoTime();
        Piece piece;
        while ((piece = ready.poll()) != null) {
            int solid = compile(piece.format, piece.solid), translucent = compile(piece.format, piece.translucent);
            if (solid != 0) solidLists.add(solid);
            if (translucent != 0) translucentLists.add(translucent);
            uploaded++;
            if (System.nanoTime() >= deadlineNanos) break;
        }
        uploadNanos += System.nanoTime() - started;
    }

    private static int compile(VertexFormat format, int[] data) {
        if (data.length == 0) return 0;
        if (uploadBuffer == null) uploadBuffer = new BufferBuilder(1 << 16);
        uploadBuffer.begin(GL11.GL_QUADS, format);
        uploadBuffer.addVertexData(data);
        uploadBuffer.finishDrawing();
        int list = GLAllocation.generateDisplayLists(1);
        liveLists++;
        GlStateManager.glNewList(list, GL11.GL_COMPILE);
        new WorldVertexBufferUploader().draw(uploadBuffer);
        GlStateManager.glEndList();
        return list;
    }

    void drawSolid() {
        for (int i = 0; i < solidLists.size(); i++) GlStateManager.callList(solidLists.getInt(i));
    }

    void drawTranslucent() {
        for (int i = 0; i < translucentLists.size(); i++) GlStateManager.callList(translucentLists.getInt(i));
    }

    /** Render thread only. */
    void free() {
        delete(solidLists);
        delete(translucentLists);
        ready.clear();
    }

    private static void delete(IntArrayList lists) {
        for (int i = 0; i < lists.size(); i++) {
            GLAllocation.deleteDisplayLists(lists.getInt(i));
            liveLists--;
        }
        lists.clear();
    }
}
