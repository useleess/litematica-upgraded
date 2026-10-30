package com.useleess.litematicaupgraded.perf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.render.LitematicaRenderer;
import com.useleess.litematicaupgraded.litematica.render.schematic.ChunkRenderDispatcherLitematica;
import com.useleess.litematicaupgraded.litematica.render.schematic.RenderChunkSchematicVbo;
import com.useleess.litematicaupgraded.litematica.render.schematic.RenderGlobalSchematic;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacementManager;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.GameWrap;
import com.useleess.litematicaupgraded.malilib.util.position.BlockPos;
import com.useleess.litematicaupgraded.malilib.util.position.ChunkSectionPos;
import com.useleess.litematicaupgraded.malilib.util.position.IntBoundingBox;

/**
 * The complete copy shown while a placement moves. The normal pieces leave out blocks that already match the world, so a
 * moved placement (whose ghosts are those pieces, shifted) would show holes. When a move starts, a second set of pieces is
 * built in the background from every schematic block (no world comparison, no overlay), nearest pieces first, within a
 * memory limit; while moving, each finished complete piece is drawn shifted, and a ghost is drawn only where the complete
 * copy has no finished piece yet. After the move the normal pieces come back (with their overlay colours) in one frame;
 * the complete copy is kept until the placement is deselected, so the next move shows it at once. It is the first thing
 * dropped when Java memory gets tight, and skipped when it is already low. Render thread only.
 */
public final class CompleteCopy {
    /** How long a move may wait for the complete copy's last builds before the real rebuild starts anyway. */
    public static final long MAX_HOLD_NANOS = 3_000_000_000L;

    private enum State { NEW, QUEUED, KEPT, REJECTED }

    private static final class Piece {
        RenderChunkSchematicVbo renderer;
        int x, y, z;
        double dist;
        State state = State.NEW;
        int submitGen;
        long bytes;
        int shiftX, shiftY, shiftZ;
        boolean deleted;

        void delete() {
            if (deleted) return;
            deleted = true;
            renderer.deleteGlResources();
        }
    }

    private static final List<Piece> PIECES = new ArrayList<>();
    private static final Map<Long, Piece> BY_POS = new HashMap<>();
    /** Pieces of the placement that were too far away to be part of the copy (their ghosts stay). */
    private static final Set<Long> OUT_OF_RANGE = new HashSet<>();
    private static SchematicPlacement placement;
    private static SchematicPlacement selectedAtStart;
    private static BlockPos origin;
    private static int next, decided;
    private static long budget, used;
    private static String source = "none";
    private static boolean budgetHit, stale;
    private static long lastMemoryCheck;
    private static int skippedLowMemory, droppedTight, made;

    private CompleteCopy() {}

    public static boolean enabled() {
        return PerfConfigs.MOVE_FULL_COPY.getBooleanValue() && PerfGuard.isOff(PerfGuard.Feature.FULL_COPY) == false;
    }

    private static boolean javaLow() {
        Runtime rt = Runtime.getRuntime();
        return PerfMath.javaMemoryLow(MemoryBudget.maxMemory(), rt.totalMemory() - rt.freeMemory());
    }

    /** The limit for a copy made now: the slider, else 1/4 of the free graphics memory, else 128 MB. */
    public static long budgetNow() {
        int slider = PerfConfigs.FULL_COPY_BUDGET_MB.getIntegerValue();
        long nvx = GpuMemory.nvxFreeKb(), ati = nvx > 0 ? -1 : GpuMemory.atiFreeKb();
        source = PerfMath.copyBudgetSource(slider, nvx, ati);
        return PerfMath.copyBudgetBytes(slider, nvx, ati);
    }

    /** A move of this placement starts (the placement was at {@code originBefore}). */
    public static void onMoveStart(SchematicPlacement target, BlockPos originBefore) {
        if (enabled() == false) {
            drop();
            return;
        }
        if (placement == target && stale == false && budgetNow() >= used) {
            return; // the kept copy is shown at once (the limit is checked again at each move start)
        }
        drop();
        if (javaLow()) {
            skippedLowMemory++;
            return;
        }
        PerfWorldRenderer worldRenderer = (PerfWorldRenderer) LitematicaRenderer.getInstance().getWorldRenderer();
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        if (camera == null) return;
        budget = budgetNow();
        used = 0;
        budgetHit = false;
        stale = false;
        int cx = (int) Math.floor(camera.posX) >> 4, cz = (int) Math.floor(camera.posZ) >> 4;
        int range = GameWrap.getRenderDistanceChunks();
        Map<Long, List<IntBoundingBox>> boxes = boxesBefore(target, originBefore);

        for (Map.Entry<Long, List<IntBoundingBox>> e : boxes.entrySet()) {
            net.minecraft.util.math.BlockPos p = net.minecraft.util.math.BlockPos.fromLong(e.getKey());
            if (Math.abs((p.getX() >> 4) - cx) > range || Math.abs((p.getZ() >> 4) - cz) > range) {
                OUT_OF_RANGE.add(e.getKey());
                continue;
            }
            Piece piece = new Piece();
            piece.x = p.getX();
            piece.y = p.getY();
            piece.z = p.getZ();
            double dx = piece.x + 8 - camera.posX, dy = piece.y + 8 - camera.posY, dz = piece.z + 8 - camera.posZ;
            piece.dist = dx * dx + dy * dy + dz * dz;
            PIECES.add(piece);
        }
        PIECES.sort(Comparator.comparingDouble(p -> p.dist));
        for (Piece piece : PIECES) {
            piece.renderer = worldRenderer.perf$newRenderer(piece.x, piece.y, piece.z);
            ((PerfChunk) piece.renderer).perf$makeComplete(boxes.get(key(piece.x, piece.y, piece.z)));
            BY_POS.put(key(piece.x, piece.y, piece.z), piece);
        }
        placement = target;
        selectedAtStart = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();
        origin = originBefore;
        next = 0;
        decided = 0;
        made++;
    }

    /**
     * The placement's boxes where its blocks still are in the schematic world (at {@code originBefore}; the placement itself
     * already has the new origin), cut into 16-block pieces and keyed by the piece's corner.
     */
    private static Map<Long, List<IntBoundingBox>> boxesBefore(SchematicPlacement target, BlockPos originBefore) {
        SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
        BlockPos now = target.getPosition();
        int sx = now.getX() - originBefore.getX(), sy = now.getY() - originBefore.getY(), sz = now.getZ() - originBefore.getZ();
        Map<Long, List<IntBoundingBox>> out = new HashMap<>();
        for (ChunkSectionPos p : manager.getAllTouchedSubChunks()) {
            for (IntBoundingBox b : manager.getTouchedBoxesInSubChunk(p)) {
                int x0 = b.minX - sx, y0 = b.minY - sy, z0 = b.minZ - sz, x1 = b.maxX - sx, y1 = b.maxY - sy, z1 = b.maxZ - sz;
                for (int px = x0 >> 4; px <= x1 >> 4; px++) {
                    for (int py = y0 >> 4; py <= y1 >> 4; py++) {
                        for (int pz = z0 >> 4; pz <= z1 >> 4; pz++) {
                            IntBoundingBox cut = new IntBoundingBox(Math.max(x0, px << 4), Math.max(y0, py << 4), Math.max(z0, pz << 4),
                                    Math.min(x1, (px << 4) + 15), Math.min(y1, (py << 4) + 15), Math.min(z1, (pz << 4) + 15));
                            out.computeIfAbsent(key(px << 4, py << 4, pz << 4), k -> new ArrayList<>()).add(cut);
                        }
                    }
                }
            }
        }
        return out;
    }

    private static long key(int x, int y, int z) {
        return new net.minecraft.util.math.BlockPos(x, y, z).toLong();
    }

    /** Every frame: keeps the copy honest (selection, memory), takes finished pieces in order, queues the next ones. */
    public static void tick() {
        if (placement == null) return;
        SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
        if (manager.getSelectedSchematicPlacement() != selectedAtStart || manager.getAllSchematicPlacements().contains(placement) == false) {
            drop();
            return;
        }
        long now = System.nanoTime();
        if (now - lastMemoryCheck > 1_000_000_000L || MemoryBudget.maxMemory() != Runtime.getRuntime().maxMemory()) {
            lastMemoryCheck = now;
            if (javaLow()) {
                droppedTight++;
                drop();
                return;
            }
        }

        // take finished pieces in the order they were queued (nearest first): a piece is kept while it fits the limit;
        // after the first one that does not, no further piece is kept
        while (decided < next) {
            Piece p = PIECES.get(decided);
            PerfChunk chunk = (PerfChunk) p.renderer;
            if (chunk.perf$dataGeneration() <= p.submitGen || chunk.perf$isBuilding()) break;
            p.bytes = chunk.perf$uploadedBytes();
            if (budgetHit == false && PerfMath.fitsBudget(used, p.bytes, budget)) {
                p.state = State.KEPT;
                used += p.bytes;
            } else {
                budgetHit = true;
                p.state = State.REJECTED;
                p.delete();
            }
            decided++;
        }

        if (budgetHit) {
            for (int i = decided; i < next; i++) ((PerfChunk) PIECES.get(i).renderer).perf$cancelCompile();
            for (int i = decided; i < PIECES.size(); i++) PIECES.get(i).state = State.REJECTED;
            decided = next = PIECES.size();
            return;
        }

        if (stale || GhostMove.isActive() == false) return;
        PerfWorldRenderer worldRenderer = (PerfWorldRenderer) LitematicaRenderer.getInstance().getWorldRenderer();
        ChunkRenderDispatcherLitematica dispatcher = worldRenderer.perf$dispatcher();
        if (dispatcher == null) return;
        int threads = worldRenderer.perf$buildThreads();
        int inFlight = Math.max(4, threads * 2);
        while (next < PIECES.size() && next - decided < inFlight) {
            Piece p = PIECES.get(next);
            p.submitGen = ((PerfChunk) p.renderer).perf$dataGeneration();
            boolean queued = threads > 0 ? dispatcher.updateChunkLater(p.renderer) : dispatcher.updateChunkNow(p.renderer);
            if (queued == false) break;
            p.renderer.clearNeedsUpdate();
            p.state = State.QUEUED;
            next++;
        }
    }

    /** True while pieces are still to be built (the move's real rebuild waits a little for them). */
    public static boolean isBuilding() {
        return placement != null && stale == false && budgetHit == false && decided < PIECES.size();
    }

    /** The real copy is about to change the schematic world: unfinished pieces would read the new blocks, so they stop; the copy is made again on the next move. */
    public static void beforeWorldChange() {
        if (isBuilding() == false) return;
        for (int i = decided; i < next; i++) ((PerfChunk) PIECES.get(i).renderer).perf$cancelCompile();
        stale = true;
    }

    /** The finished pieces, moved to where the placement is now ({@code now} = its origin), for drawing. */
    public static void addDrawn(SchematicPlacement target, BlockPos now, List<RenderChunkSchematicVbo> out) {
        if (placement != target || origin == null) return;
        int ox = now.getX() - origin.getX(), oy = now.getY() - origin.getY(), oz = now.getZ() - origin.getZ();
        for (Piece p : PIECES) {
            if (p.state != State.KEPT) continue;
            if (p.shiftX != ox || p.shiftY != oy || p.shiftZ != oz) {
                p.renderer.setPosition(p.x + ox, p.y + oy, p.z + oz);
                p.shiftX = ox;
                p.shiftY = oy;
                p.shiftZ = oz;
            }
            out.add(p.renderer);
        }
    }

    /**
     * Whether the complete copy already shows the 16-block piece whose corner was at (x, y, z) when the placement's origin was
     * {@code ghostOrigin}: every complete piece it overlaps is finished (or holds nothing of the placement).
     */
    public static boolean covers(SchematicPlacement target, BlockPos ghostOrigin, int x, int y, int z) {
        if (placement != target || origin == null) return false;
        int bx = x - ghostOrigin.getX() + origin.getX(), by = y - ghostOrigin.getY() + origin.getY(), bz = z - ghostOrigin.getZ() + origin.getZ();
        for (int px = bx >> 4; px <= (bx + 15) >> 4; px++) {
            for (int py = by >> 4; py <= (by + 15) >> 4; py++) {
                for (int pz = bz >> 4; pz <= (bz + 15) >> 4; pz++) {
                    Piece p = BY_POS.get(key(px << 4, py << 4, pz << 4));
                    if (p != null && p.state != State.KEPT) return false;
                    if (p == null && OUT_OF_RANGE.contains(key(px << 4, py << 4, pz << 4))) return false;
                }
            }
        }
        return true;
    }

    /** Throws the copy away (deselected, changed, memory tight, renderers reloaded). */
    public static void drop() {
        if (PIECES.isEmpty() && placement == null) return;
        RenderGlobalSchematic worldRenderer = LitematicaRenderer.getInstance().getWorldRenderer();
        for (Piece p : PIECES) {
            ((PerfChunk) p.renderer).perf$cancelCompile();
            ((PerfChunk) p.renderer).perf$forgetTileEntities(worldRenderer);
            p.delete();
        }
        forget();
    }

    /** Renderers reloaded: the pieces' buffers are gone with them; forget the copy without touching them. */
    public static void forget() {
        PIECES.clear();
        BY_POS.clear();
        OUT_OF_RANGE.clear();
        placement = null;
        selectedAtStart = null;
        origin = null;
        next = decided = 0;
        used = 0;
        budgetHit = false;
        stale = false;
    }

    // ------------------------------------------------------------------------------------------------ numbers for tests and F3

    public static boolean isKeptFor(SchematicPlacement p) { return placement == p && p != null; }
    public static boolean exists() { return placement != null; }
    public static int pieceCount() { return PIECES.size(); }
    public static int keptCount() {
        int n = 0;
        for (Piece p : PIECES) if (p.state == State.KEPT) n++;
        return n;
    }
    public static int rejectedCount() {
        int n = 0;
        for (Piece p : PIECES) if (p.state == State.REJECTED) n++;
        return n;
    }
    public static long usedBytes() { return used; }
    public static long budgetBytes() { return budget; }
    public static String budgetSource() { return source; }
    public static boolean budgetHit() { return budgetHit; }
    public static boolean isStale() { return stale; }
    public static int skippedLowMemory() { return skippedLowMemory; }
    public static int droppedTight() { return droppedTight; }
    public static int made() { return made; }

    /** The largest camera distance among kept pieces and the smallest among the others (nearest-first check); NaN when none. */
    public static double[] keptVsOtherDistance() {
        double maxKept = Double.NaN, minOther = Double.NaN;
        for (Piece p : PIECES) {
            double d = Math.sqrt(p.dist);
            if (p.state == State.KEPT) maxKept = Double.isNaN(maxKept) ? d : Math.max(maxKept, d);
            else minOther = Double.isNaN(minOther) ? d : Math.min(minOther, d);
        }
        return new double[] {maxKept, minOther};
    }

    /** Model quads (vertices / 4) of the kept pieces' solid-ish layers, from their uploads. */
    public static long keptQuadBytes() {
        long n = 0;
        for (Piece p : PIECES) if (p.state == State.KEPT) n += p.bytes;
        return n;
    }

    /** Every piece of the copy, finished or not (tests check what is drawn). */
    public static List<RenderChunkSchematicVbo> keptRenderers() {
        List<RenderChunkSchematicVbo> out = new ArrayList<>();
        for (Piece p : PIECES) if (p.state == State.KEPT) out.add(p.renderer);
        return out;
    }
}
