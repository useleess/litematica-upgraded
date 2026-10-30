package com.useleess.litematicaupgraded.perf;

import java.util.ArrayList;
import java.util.List;

import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.render.LitematicaRenderer;
import com.useleess.litematicaupgraded.litematica.render.schematic.RenderChunkSchematicVbo;
import com.useleess.litematicaupgraded.litematica.render.schematic.RenderGlobalSchematic;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;
import com.useleess.litematicaupgraded.malilib.util.position.BlockPos;

/**
 * Moving a placement makes Litematica copy and rebuild every chunk it touches, for every single step. While a placement is
 * moved we instead keep the chunks that were already built ("ghosts") and draw them shifted by the distance moved; all
 * copying is held back. Once the placement has stayed still for a moment the real copy and rebuild run in the background
 * (ghosts still shown), and when they are done the view switches over in one frame. Idea and tuning from Litematica Turbo.
 * Render thread only.
 */
public final class GhostMove {
    /** How long the placement must stay still before the real rebuild starts. */
    public static final long SETTLE_NANOS = 400_000_000L;
    /** The ghosts are dropped after this even if the real rebuild is not reported done (a safety net). */
    private static final long MAX_NANOS = 20_000_000_000L;

    private static final List<Ghost> GHOSTS = new ArrayList<>();
    private static final List<RenderChunkSchematicVbo> RENDERERS = new ArrayList<>();
    private static final List<RenderChunkSchematicVbo> DRAW = new ArrayList<>();

    private static SchematicPlacement placement;
    private static BlockPos ghostOrigin;
    private static long startNanos;
    private static long lastMoveNanos;
    private static boolean inSetOrigin;
    private static BlockPos originBeforeSet;
    private static BlockPos offset = new BlockPos(0, 0, 0);

    private GhostMove() {}

    public static boolean isActive() {
        return placement != null;
    }

    /** The shifted chunks drawn instead of the real ones. */
    public static List<RenderChunkSchematicVbo> renderers() {
        return RENDERERS;
    }

    public static BlockPos currentOffset() {
        return offset;
    }

    /**
     * True while the placement is still moving, so copying and rebuilding should wait; also a little longer while the
     * complete copy still builds (it reads the schematic world at the old place, which the real copy then changes).
     */
    public static boolean isHoldingWork() {
        if (isActive() == false) return false;
        long still = System.nanoTime() - lastMoveNanos;
        return still < SETTLE_NANOS || (still < CompleteCopy.MAX_HOLD_NANOS && CompleteCopy.isBuilding());
    }

    /**
     * What is drawn while moving: the finished pieces of the complete copy, shifted, and the ghosts of the places the
     * complete copy does not show yet.
     */
    public static List<RenderChunkSchematicVbo> drawList() {
        DRAW.clear();
        if (isActive() == false) return DRAW;
        BlockPos now = placement.getPosition();
        CompleteCopy.addDrawn(placement, now, DRAW);
        boolean copy = DRAW.isEmpty() == false || CompleteCopy.isKeptFor(placement);
        for (Ghost g : GHOSTS) {
            if (copy == false || CompleteCopy.covers(placement, ghostOrigin, g.x, g.y, g.z) == false) DRAW.add(g.renderer);
        }
        return DRAW;
    }

    public static boolean isInSetOrigin() {
        return inSetOrigin;
    }

    /** Called at the start of SchematicPlacementManager.setOrigin. */
    public static void beforeSetOrigin(SchematicPlacement target) {
        inSetOrigin = true;
        originBeforeSet = target.getPosition();
    }

    /** Called when SchematicPlacementManager.setOrigin returns. */
    public static void afterSetOrigin(SchematicPlacement target) {
        inSetOrigin = false;
        BlockPos before = originBeforeSet;
        BlockPos after = target.getPosition();
        originBeforeSet = null;
        if (before == null || before.equals(after)) return;
        if (isActive() && placement != target) end();
        if (!isActive() && (!eligible(target) || !start(target, before))) return;
        lastMoveNanos = System.nanoTime();
        offset = new BlockPos(after.getX() - ghostOrigin.getX(), after.getY() - ghostOrigin.getY(), after.getZ() - ghostOrigin.getZ());
        for (Ghost g : GHOSTS) g.renderer.setPosition(g.x + offset.getX(), g.y + offset.getY(), g.z + offset.getZ());
        PerfStats.recordGhostMove();
    }

    /** Only a lone placement: the built chunks then hold nothing else that would move along with it. */
    private static boolean eligible(SchematicPlacement target) {
        if (!PerfConfigs.GHOST_MOVE.getBooleanValue() || !target.isEnabled()) return false;
        for (SchematicPlacement other : DataManager.getSchematicPlacementManager().getAllSchematicPlacements()) {
            if (other != target && other.isEnabled()) return false;
        }
        return true;
    }

    private static boolean start(SchematicPlacement target, BlockPos originBefore) {
        RenderGlobalSchematic worldRenderer = LitematicaRenderer.getInstance().getWorldRenderer();
        List<RenderChunkSchematicVbo> detached = ((PerfWorldRenderer) worldRenderer).perf$detachChunkRenderers();
        if (detached == null || detached.isEmpty()) return false;
        for (RenderChunkSchematicVbo renderer : detached) {
            // keep the last finished build; a build still running would upload into a ghost later
            ((PerfChunk) renderer).perf$cancelCompile();
            net.minecraft.util.math.BlockPos p = renderer.getPosition();
            GHOSTS.add(new Ghost(renderer, p.getX(), p.getY(), p.getZ()));
            RENDERERS.add(renderer);
        }
        placement = target;
        ghostOrigin = originBefore;
        startNanos = System.nanoTime();

        try {
            CompleteCopy.onMoveStart(target, originBefore);
        } catch (RuntimeException e) {
            PerfGuard.fail(PerfGuard.Feature.FULL_COPY, e);
            CompleteCopy.forget();
        }
        return true;
    }

    /** Every frame: switch to the real chunks once they are all rebuilt. */
    public static void tick(PerfWorldRenderer worldRenderer, boolean copiesQueued) {
        if (!isActive() || isHoldingWork()) return;
        CompleteCopy.beforeWorldChange();
        long now = System.nanoTime();
        if ((!copiesQueued && worldRenderer.perf$isIdle()) || now - startNanos > MAX_NANOS) end();
    }

    /** Drops the ghosts (rebuild done, or something other than a move happened). */
    public static void end() {
        if (!isActive()) return;
        RenderGlobalSchematic worldRenderer = LitematicaRenderer.getInstance().getWorldRenderer();
        for (Ghost g : GHOSTS) {
            ((PerfChunk) g.renderer).perf$forgetTileEntities(worldRenderer);
            g.renderer.deleteGlResources();
        }
        GHOSTS.clear();
        RENDERERS.clear();
        DRAW.clear();
        placement = null;
        ghostOrigin = null;
        offset = new BlockPos(0, 0, 0);
        worldRenderer.markNeedsUpdate();
    }

    /** World unloaded / renderers reloaded: the ghosts' buffers go with them, and the complete copy. */
    public static void reset() {
        end();
        CompleteCopy.drop();
    }

    /** A change other than a move (rotate, mirror, placement added / removed): ghosts end and the complete copy no longer fits. */
    public static void endAndDropCopy() {
        end();
        CompleteCopy.drop();
    }

    private static final class Ghost {
        final RenderChunkSchematicVbo renderer;
        final int x, y, z;

        Ghost(RenderChunkSchematicVbo renderer, int x, int y, int z) {
            this.renderer = renderer;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}
