package com.useleess.litematicaupgraded.preview;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.ForgeHooksClient;
import org.lwjgl.opengl.GL11;

import com.useleess.litematicaupgraded.litematica.Litematica;
import com.useleess.litematicaupgraded.litematica.schematic.LoadedSchematic;
import com.useleess.litematicaupgraded.litematica.schematic.SchematicRegion;
import com.useleess.litematicaupgraded.litematica.schematic.container.BlockContainer;
import com.useleess.litematicaupgraded.perf.PerfMath;

/** Reads a schematic file and turns its blocks (shrunk to a smaller schematic when it is big) into a {@link PreviewModel}, in sections on several threads. Never runs in a frame. */
final class PreviewBuilder {
    private PreviewBuilder() {}

    /** One region of the file as a box of blocks. */
    private static final class Region implements BlockSelection.Grid {
        final BlockContainer container;
        final int sizeX, sizeY, sizeZ;
        final int minX, minY, minZ;

        Region(SchematicRegion region) {
            container = region.getBlockContainer();
            sizeX = container.getSize().getX();
            sizeY = container.getSize().getY();
            sizeZ = container.getSize().getZ();
            // a negative size means the region's other corner lies on the negative side of its position
            minX = region.getRelativePosition().getX() + Math.min(0, region.getSize().getX() + 1);
            minY = region.getRelativePosition().getY() + Math.min(0, region.getSize().getY() + 1);
            minZ = region.getRelativePosition().getZ() + Math.min(0, region.getSize().getZ() + 1);
        }

        @Override
        public int sizeX() {
            return sizeX;
        }

        @Override
        public int sizeY() {
            return sizeY;
        }

        @Override
        public int sizeZ() {
            return sizeZ;
        }

        @Override
        public boolean solid(int x, int y, int z) {
            return !container.getBlockState(x, y, z).isAirMaterial();
        }
    }

    /** The layers drawn in the first pass (opaque and cut-out), then the translucent ones that are drawn over them. */
    private static final BlockRenderLayer[][] PASS_LAYERS = {
        {BlockRenderLayer.SOLID, BlockRenderLayer.CUTOUT_MIPPED, BlockRenderLayer.CUTOUT}, {BlockRenderLayer.TRANSLUCENT}};

    /** Per build thread: the builders of a section's two passes, reused for its next section ({@link PreviewModel} says why). */
    private static final ThreadLocal<BufferBuilder[]> PASSES =
            ThreadLocal.withInitial(() -> new BufferBuilder[] {new BufferBuilder(1 << 16), new BufferBuilder(1 << 12)});

    private static ExecutorService pool;
    private static volatile long vertexCap = PreviewMath.FULL_MAX_VERTICES;

    /** Tests: the most vertices the full model may have (a schematic over it keeps its shrunk model). */
    static void setVertexCapForTest(long vertices) {
        vertexCap = vertices;
    }

    static void resetVertexCap() {
        vertexCap = PreviewMath.FULL_MAX_VERTICES;
    }

    /** The build threads, as many as Litematica's own chunk builders ({@link PerfMath#workers}); made when the first preview is built. */
    private static synchronized ExecutorService pool() {
        if (pool == null) {
            int threads = PerfMath.workers(Runtime.getRuntime().availableProcessors(), PerfMath.memoryThreadCap(Runtime.getRuntime().maxMemory()));
            AtomicInteger number = new AtomicInteger();
            pool = Executors.newFixedThreadPool(threads, r -> {
                Thread t = new Thread(r, "Litematica Upgraded preview build " + number.incrementAndGet());
                t.setDaemon(true);
                return t;
            });
        }
        return pool;
    }

    /**
     * Reads the file and builds its preview in sections on the build threads. A big file first gets a rough copy (handed to {@code rough}),
     * then the shrunk one (handed to {@code full}); each is handed over at once, empty, and fills with finished sections. When the shown
     * schematic is shrunk and small enough, every block of the file is built last (handed to {@code detail}) for the view to use when zoomed in.
     */
    static void build(Path file, BooleanSupplier cancelled, PreviewTiming timing, Consumer<PreviewModel> rough, Consumer<PreviewModel> full,
                      Consumer<PreviewModel> detail) throws InterruptedException {
        long deadline = System.currentTimeMillis() + PreviewMath.BUILD_TIMEOUT_MS;
        BooleanSupplier stop = () -> cancelled.getAsBoolean() || System.currentTimeMillis() > deadline;
        Optional<LoadedSchematic> loaded = LoadedSchematic.tryLoadSchematic(file);
        if (!loaded.isPresent()) throw new IllegalStateException("the file cannot be read as a schematic");
        timing.readMs = timing.elapsedMs();

        List<Region> regions = new ArrayList<>();
        for (SchematicRegion region : loaded.get().schematic.getRegions().values()) regions.add(new Region(region));
        int[] box = emptyBounds();
        for (Region region : regions) {
            include(box, region.minX, region.minY, region.minZ);
            include(box, region.minX + region.sizeX - 1, region.minY + region.sizeY - 1, region.minZ + region.sizeZ - 1);
        }
        int sizeX = box[3] - box[0] + 1, sizeY = box[4] - box[1] + 1, sizeZ = box[5] - box[2] + 1;

        int step = PreviewMath.stepFor(Math.max(sizeX, Math.max(sizeY, sizeZ)));
        DownsampledGrid<IBlockState> fine = new DownsampledGrid<>(sizeX, sizeY, sizeZ, step, IBlockState::isOpaqueCube);
        long[] solid = {0};
        for (Region region : regions) {
            BlockSelection.forEachSolid(region, stop, (x, y, z) -> {
                solid[0]++;
                fine.offer(region.minX + x - box[0], region.minY + y - box[1], region.minZ + z - box[2], region.container.getBlockState(x, y, z).vanillaState());
            });
        }
        checkNotStopped(cancelled, stop);
        if (fine.count() == 0) throw new IllegalStateException("the schematic has no blocks");
        DownsampledGrid<IBlockState> shown = fine;
        while (shown.count() > PreviewMath.MAX_BLOCKS) shown = shown.coarser(2);
        timing.downsampleMs = timing.elapsedMs() - timing.readMs;

        long buildStart = timing.elapsedMs();
        if (shown.count() > PreviewMath.ROUGH_BLOCKS) {
            DownsampledGrid<IBlockState> roughGrid = shown;
            while (roughGrid.count() > PreviewMath.ROUGH_BLOCKS) roughGrid = roughGrid.coarser(2);
            render(roughGrid, false, cancelled, stop, rough);
        }
        render(shown, true, cancelled, stop, full);
        timing.buildMs = timing.elapsedMs() - buildStart;

        if (shown.unit > 1 && solid[0] <= PreviewMath.FULL_MAX_BLOCKS) {
            long detailStart = timing.elapsedMs();
            long detailDeadline = System.currentTimeMillis() + PreviewMath.BUILD_TIMEOUT_MS;
            try {
                buildDetail(regions, box, sizeX, sizeY, sizeZ, timing, cancelled,
                        () -> cancelled.getAsBoolean() || System.currentTimeMillis() > detailDeadline, detail);
            } catch (IllegalStateException e) {
                Litematica.LOGGER.warn("Litematica Upgraded: the full 3D preview of {} was given up, the shrunk one stays", file, e);
            }
            timing.detailBuildMs = timing.elapsedMs() - detailStart;
        }
    }

    /**
     * Every block of the file as its own cell, handed over once all of it is built; nothing is handed over when its vertex data passes the
     * cap while it is built (the shrunk model stays).
     */
    private static void buildDetail(List<Region> regions, int[] box, int sizeX, int sizeY, int sizeZ, PreviewTiming timing, BooleanSupplier cancelled,
                                    BooleanSupplier stop, Consumer<PreviewModel> out) throws InterruptedException {
        PreviewBlockAccess access = new PreviewBlockAccess();
        LongArrayList positions = new LongArrayList();
        for (Region region : regions) {
            BlockSelection.forEachSolid(region, stop, (x, y, z) -> {
                long packed = BlockSelection.pack(region.minX + x - box[0], region.minY + y - box[1], region.minZ + z - box[2]);
                positions.add(packed);
                access.put(cellPos(packed), region.container.getBlockState(x, y, z).vanillaState());
            });
        }
        checkNotStopped(cancelled, stop);
        List<LongArrayList> sections = PreviewSections.split(positions);
        PreviewModel model = new PreviewModel(sizeX, sizeY, sizeZ, 1, positions.size(), sections.size(), true);
        long cap = vertexCap;
        BooleanSupplier overCap = () -> !PreviewMath.fullModelAllowed(positions.size(), model.vertexCount(), cap);
        try {
            runSections(model, access, sections, cancelled, () -> stop.getAsBoolean() || overCap.getAsBoolean());
        } catch (IllegalStateException e) {
            if (!overCap.getAsBoolean()) throw e;
            Litematica.LOGGER.info("Litematica Upgraded: the 3D preview stays shrunk, the full model passed {} vertices", cap);
            return;
        }
        timing.detailBlocks = positions.size();
        timing.detailVertices = model.vertexCount();
        out.accept(model);
    }

    /** The cells of the shrunk schematic drawn by the game's block renderer, one section per task: textured, full-bright, culled between neighbours. */
    private static void render(DownsampledGrid<IBlockState> shown, boolean full, BooleanSupplier cancelled, BooleanSupplier stop,
                               Consumer<PreviewModel> out) throws InterruptedException {
        PreviewBlockAccess access = new PreviewBlockAccess();
        shown.forEachCell((packed, state) -> access.put(cellPos(packed), state));
        List<LongArrayList> sections = PreviewSections.split(shown);
        PreviewModel model = new PreviewModel(shown.sizeX, shown.sizeY, shown.sizeZ, shown.unit, shown.count(), sections.size(), full);
        out.accept(model);
        runSections(model, access, sections, cancelled, stop);
    }

    /** Builds the sections of a model side by side and hands each finished one to it. */
    private static void runSections(PreviewModel model, PreviewBlockAccess access, List<LongArrayList> sections, BooleanSupplier cancelled,
                                    BooleanSupplier stop) throws InterruptedException {
        Minecraft mc = Minecraft.getMinecraft();
        BlockRendererDispatcher dispatcher = mc.getBlockRendererDispatcher();
        int ambientOcclusion = mc.gameSettings.ambientOcclusion;
        mc.gameSettings.ambientOcclusion = 0;
        try {
            SectionRunner.runAll(pool(), sections, stop, positions -> buildSection(model, dispatcher, access, positions, stop));
        } finally {
            mc.gameSettings.ambientOcclusion = ambientOcclusion;
        }
        checkNotStopped(cancelled, stop);
    }

    private static BlockPos cellPos(long packed) {
        return new BlockPos(BlockSelection.x(packed), BlockSelection.y(packed), BlockSelection.z(packed));
    }

    /** One section as two lots of vertex data, handed to the model unless stopped: opaque and cut-out layers, then the translucent one. */
    private static void buildSection(PreviewModel model, BlockRendererDispatcher dispatcher, PreviewBlockAccess access, LongArrayList positions,
                                     BooleanSupplier stop) {
        BufferBuilder[] passes = PASSES.get();
        int drawing = -1;
        try {
            for (int pass = 0; pass < passes.length; pass++) {
                passes[pass].begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                drawing = pass;
                for (BlockRenderLayer layer : PASS_LAYERS[pass]) {
                    ForgeHooksClient.setRenderLayer(layer);
                    for (int i = 0; i < positions.size(); i++) {
                        if ((i & 255) == 0 && stop.getAsBoolean()) return;
                        drawBlock(dispatcher, passes[pass], access, cellPos(positions.getLong(i)), layer);
                    }
                }
                passes[pass].finishDrawing();
                drawing = -1;
            }
            if (!stop.getAsBoolean()) model.addPiece(passes[0], passes[1]);
        } finally {
            if (drawing >= 0) passes[drawing].finishDrawing();
            ForgeHooksClient.setRenderLayer(null);
        }
    }

    private static void drawBlock(BlockRendererDispatcher dispatcher, BufferBuilder buffer, PreviewBlockAccess access, BlockPos pos,
                                  BlockRenderLayer layer) {
        IBlockState state = access.getBlockState(pos);
        if (state.getRenderType() == EnumBlockRenderType.INVISIBLE || !state.getBlock().canRenderInLayer(state, layer)) return;
        try {
            dispatcher.renderBlock(state, pos, access, buffer);
        } catch (LinkageError | RuntimeException e) {
            Litematica.LOGGER.debug("Litematica Upgraded: {} is left out of the 3D preview", state, e);
        }
    }

    /** min x, y, z then max x, y, z of nothing yet; {@link #include} grows it. */
    private static int[] emptyBounds() {
        return new int[] {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
    }

    private static void include(int[] bounds, int x, int y, int z) {
        bounds[0] = Math.min(bounds[0], x);
        bounds[1] = Math.min(bounds[1], y);
        bounds[2] = Math.min(bounds[2], z);
        bounds[3] = Math.max(bounds[3], x);
        bounds[4] = Math.max(bounds[4], y);
        bounds[5] = Math.max(bounds[5], z);
    }

    private static void checkNotStopped(BooleanSupplier cancelled, BooleanSupplier stop) {
        if (cancelled.getAsBoolean()) throw new CancelledException();
        if (stop.getAsBoolean()) throw new IllegalStateException("building the preview took too long");
    }

    /** The screen moved on to another file or closed; nobody wants this model any more. */
    static final class CancelledException extends RuntimeException {
        CancelledException() {
            super("preview cancelled", null, false, false);
        }
    }
}
