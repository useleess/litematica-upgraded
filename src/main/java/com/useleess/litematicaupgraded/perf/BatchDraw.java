package com.useleess.litematicaupgraded.perf;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GLContext;

import net.minecraft.client.renderer.ChunkRenderContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.util.math.BlockPos;

import com.useleess.litematicaupgraded.litematica.render.schematic.RenderChunkSchematicVbo;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.RenderWrap;
import com.useleess.litematicaupgraded.mixin.IMixinVertexBuffer;

/**
 * Fewer, bigger draw jobs: the built pieces of a 4 x 4 x 4 piece region are copied (on the graphics card's side, read
 * back once) into one region buffer, with their vertex positions moved to the region's corner. A frame then draws all
 * listed pieces of a region with ONE call (glMultiDrawArrays draws exactly the listed pieces' ranges, so the picture is
 * the same as drawing them one by one). A region is (re)made only after its pieces stayed unchanged for a moment; until
 * then (and for pieces it does not hold) pieces are drawn one by one as before. Opaque layers only (see-through ones
 * are re-sorted per piece). Needs OpenGL 1.5 (every PC that runs 1.12 with VBOs); without it everything is drawn one by
 * one. Render thread only.
 */
public final class BatchDraw {
    /** A piece must stay unchanged this many frames before its region is made. */
    private static final int QUIET_FRAMES = 20;
    private static final int MAX_REGIONS_PER_FRAME = 1;
    private static final int MAX_READBACK_BYTES_PER_FRAME = 4 * 1024 * 1024;
    /** A region not drawn for this many frames gives its buffer back. */
    private static final int EVICT_FRAMES = 600;

    private static final class Member {
        long pos;
        int gen;
        int first;
        int count;
    }

    private static final class Region {
        int vbo;
        int ox, oy, oz;
        long lastUsed;
        final Map<RenderChunkSchematicVbo, Member> members = new IdentityHashMap<>();
    }

    /** What a piece looked like when it was last seen: position, layer generation, since which frame. */
    private static final class Seen {
        long pos;
        int gen;
        long since;
    }

    @SuppressWarnings("unchecked")
    private static final Map<Long, Region>[] REGIONS = new Map[] {new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>()};
    @SuppressWarnings("unchecked")
    private static final Map<RenderChunkSchematicVbo, Seen>[] SEEN = new Map[] {new IdentityHashMap<>(), new IdentityHashMap<>(), new IdentityHashMap<>(), new IdentityHashMap<>()};

    private static long frame;
    private static int regionsMadeThisFrame;
    private static long readbackThisFrame;
    private static ByteBuffer scratch;
    private static IntBuffer firsts = BufferUtils.createIntBuffer(64);
    private static IntBuffer counts = BufferUtils.createIntBuffer(64);
    private static Boolean supported;

    /** Draws one piece's layer the stock way (the caller's own code). */
    public interface Single {
        void draw(RenderChunk piece);
    }

    private BatchDraw() {}

    public static boolean supported() {
        if (supported == null) {
            supported = GLContext.getCapabilities().OpenGL15;
        }
        return supported;
    }

    public static boolean active() {
        return PerfConfigs.BATCH_DRAW.getBooleanValue() && PerfGuard.isOff(PerfGuard.Feature.BATCH) == false && supported()
                && RenderWrap.useVbo() && GhostMove.isActive() == false;
    }

    /** At the start of a schematic frame. */
    public static void frame() {
        frame++;
        regionsMadeThisFrame = 0;
        readbackThisFrame = 0;
        if (frame % 100 == 0) evict();
    }

    /** Draws the listed pieces of an opaque layer, region by region. */
    public static void drawLayer(ChunkRenderContainer container, int layer, List<RenderChunk> pieces, Single single) {
        Map<Long, List<RenderChunkSchematicVbo>> groups = new LinkedHashMap<>();
        for (RenderChunk rc : pieces) {
            BlockPos p = rc.getPosition();
            groups.computeIfAbsent(PerfMath.regionKey(p.getX() >> 4, p.getY() >> 4, p.getZ() >> 4), k -> new ArrayList<>(8)).add((RenderChunkSchematicVbo) rc);
        }

        for (Map.Entry<Long, List<RenderChunkSchematicVbo>> e : groups.entrySet()) {
            List<RenderChunkSchematicVbo> group = e.getValue();
            boolean quiet = true;
            for (RenderChunkSchematicVbo rc : group) quiet &= see(layer, rc);

            if (group.size() == 1) {
                single.draw(group.get(0));
                continue;
            }

            Region region = REGIONS[layer].get(e.getKey());
            List<RenderChunkSchematicVbo> inRegion = new ArrayList<>(group.size());
            boolean missing = false;

            for (RenderChunkSchematicVbo rc : group) {
                Member m = region == null ? null : region.members.get(rc);
                if (m != null && m.pos == rc.getPosition().toLong() && m.gen == ((PerfChunk) rc).perf$generation(layer)) inRegion.add(rc);
                else missing = true;
            }

            if (missing && quiet && regionsMadeThisFrame < MAX_REGIONS_PER_FRAME && readbackThisFrame < MAX_READBACK_BYTES_PER_FRAME) {
                region = make(layer, e.getKey(), group, region);
                inRegion.clear();
                if (region != null) inRegion.addAll(group);
            }

            if (inRegion.size() >= 1 && region != null) {
                drawRegion(container, layer, region, inRegion);
            }

            if (inRegion.size() < group.size()) {
                for (RenderChunkSchematicVbo rc : group) {
                    if (inRegion.contains(rc) == false) single.draw(rc);
                }
            }
        }
    }

    /** Notes the piece's state; true when it has not changed for a while. */
    private static boolean see(int layer, RenderChunkSchematicVbo rc) {
        Seen s = SEEN[layer].get(rc);
        long pos = rc.getPosition().toLong();
        int gen = ((PerfChunk) rc).perf$generation(layer);
        if (s == null) {
            s = new Seen();
            s.pos = pos;
            s.gen = gen;
            s.since = frame;
            SEEN[layer].put(rc, s);
        } else if (s.pos != pos || s.gen != gen) {
            s.pos = pos;
            s.gen = gen;
            s.since = frame;
        }
        return frame - s.since >= QUIET_FRAMES;
    }

    private static Region make(int layer, long key, List<RenderChunkSchematicVbo> listed, Region old) {
        // a region keeps the pieces it already holds (when still unchanged), so turning the camera, which lists other
        // pieces of the same region, does not make it again
        List<RenderChunkSchematicVbo> group = new ArrayList<>(listed);
        if (old != null) {
            for (Map.Entry<RenderChunkSchematicVbo, Member> e : old.members.entrySet()) {
                RenderChunkSchematicVbo rc = e.getKey();
                if (group.contains(rc) == false && e.getValue().pos == rc.getPosition().toLong() && e.getValue().gen == ((PerfChunk) rc).perf$generation(layer)
                        && PerfMath.regionKey(rc.getPosition().getX() >> 4, rc.getPosition().getY() >> 4, rc.getPosition().getZ() >> 4) == key) group.add(rc);
            }
        }
        BlockPos first = group.get(0).getPosition();
        int ox = PerfMath.regionOrigin(first.getX() >> 4), oy = PerfMath.regionOrigin(first.getY() >> 4), oz = PerfMath.regionOrigin(first.getZ() >> 4);
        int total = 0;
        for (RenderChunkSchematicVbo rc : group) total += access(rc.getVertexBufferByLayer(layer)).perf$getCount();
        int bytes = total * PerfMath.BLOCK_VERTEX_BYTES;
        if (total == 0) return null;

        if (scratch == null || scratch.capacity() < bytes) scratch = BufferUtils.createByteBuffer(Math.max(bytes, 1 << 20)).order(ByteOrder.nativeOrder());
        Region region = old != null ? old : new Region();
        region.members.clear();
        region.ox = ox;
        region.oy = oy;
        region.oz = oz;
        int vertexAt = 0;

        for (RenderChunkSchematicVbo rc : group) {
            IMixinVertexBuffer vb = access(rc.getVertexBufferByLayer(layer));
            int count = vb.perf$getCount();
            Member m = new Member();
            m.pos = rc.getPosition().toLong();
            m.gen = ((PerfChunk) rc).perf$generation(layer);
            m.first = vertexAt;
            m.count = count;
            region.members.put(rc, m);
            if (count == 0) continue;
            int start = vertexAt * PerfMath.BLOCK_VERTEX_BYTES, len = count * PerfMath.BLOCK_VERTEX_BYTES;
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vb.perf$getGlBufferId());
            scratch.clear();
            scratch.position(start).limit(start + len);
            GL15.glGetBufferSubData(GL15.GL_ARRAY_BUFFER, 0, scratch);
            scratch.clear();
            BlockPos p = rc.getPosition();
            PerfMath.shiftPositions(scratch, start, count, p.getX() - ox, p.getY() - oy, p.getZ() - oz);
            vertexAt += count;
        }

        if (region.vbo == 0) region.vbo = GL15.glGenBuffers();
        scratch.position(0).limit(bytes);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, region.vbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, scratch, GL15.GL_STATIC_DRAW);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        scratch.clear();
        region.lastUsed = frame;
        REGIONS[layer].put(key, region);
        regionsMadeThisFrame++;
        readbackThisFrame += bytes;
        DrawStats.batchedRegion();
        return region;
    }

    private static void drawRegion(ChunkRenderContainer container, int layer, Region region, List<RenderChunkSchematicVbo> pieces) {
        if (firsts.capacity() < pieces.size()) {
            firsts = BufferUtils.createIntBuffer(pieces.size() * 2);
            counts = BufferUtils.createIntBuffer(pieces.size() * 2);
        }
        firsts.clear();
        counts.clear();
        int vertices = 0;
        for (RenderChunkSchematicVbo rc : pieces) {
            Member m = region.members.get(rc);
            if (m.count == 0) continue;
            firsts.put(m.first);
            counts.put(m.count);
            vertices += m.count;
        }
        firsts.flip();
        counts.flip();
        region.lastUsed = frame;
        if (vertices == 0) return;

        RenderChunk any = pieces.get(0);
        BlockPos p = any.getPosition();
        GlStateManager.pushMatrix();
        container.preRenderChunk(any);
        GlStateManager.translate((float) (region.ox - p.getX()), (float) (region.oy - p.getY()), (float) (region.oz - p.getZ()));
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, region.vbo);
        setupBlockPointers();
        GL14.glMultiDrawArrays(GL11.GL_QUADS, firsts, counts);
        GlStateManager.popMatrix();
        DrawStats.draw(layer, vertices);
    }

    /** The same pointers as Litematica's per-piece draw (Minecraft's block vertex format). */
    public static void setupBlockPointers() {
        RenderWrap.vertexPointer(3, GL11.GL_FLOAT, 28, 0);
        RenderWrap.colorPointer(4, GL11.GL_UNSIGNED_BYTE, 28, 12);
        RenderWrap.texCoordPointer(2, GL11.GL_FLOAT, 28, 16);
        RenderWrap.setClientActiveTexture(RenderWrap.LIGHTMAP_TEX_UNIT);
        RenderWrap.texCoordPointer(2, GL11.GL_SHORT, 28, 24);
        RenderWrap.setClientActiveTexture(RenderWrap.DEFAULT_TEX_UNIT);
    }

    private static void evict() {
        for (int layer = 0; layer < 4; layer++) {
            Iterator<Region> it = REGIONS[layer].values().iterator();
            while (it.hasNext()) {
                Region r = it.next();
                if (frame - r.lastUsed > EVICT_FRAMES) {
                    if (r.vbo != 0) GL15.glDeleteBuffers(r.vbo);
                    it.remove();
                }
            }
        }
    }

    /** Renderers reloaded / world changed: every region buffer goes. */
    public static void clear() {
        for (int layer = 0; layer < 4; layer++) {
            for (Region r : REGIONS[layer].values()) {
                if (r.vbo != 0) GL15.glDeleteBuffers(r.vbo);
            }
            REGIONS[layer].clear();
            SEEN[layer].clear();
        }
    }

    /** How many region buffers exist (tests). */
    public static int regionCount() {
        int n = 0;
        for (Map<Long, Region> m : REGIONS) n += m.size();
        return n;
    }

    private static IMixinVertexBuffer access(VertexBuffer vb) {
        return (IMixinVertexBuffer) vb;
    }
}
