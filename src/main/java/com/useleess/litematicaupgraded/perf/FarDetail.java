package com.useleess.litematicaupgraded.perf;

import net.minecraft.util.math.BlockPos;

import com.useleess.litematicaupgraded.litematica.render.schematic.RenderChunkSchematicVbo;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.GameWrap;

/**
 * Less detail far away: pieces at least half the render distance away (3 pieces at least) are drawn without their outline
 * lines, which there are only a blur of thin lines but cost as many vertices as near ones. Off by itself when Java memory
 * is low, so the setting never adds work to a PC that is already struggling. Render thread only.
 */
public final class FarDetail {
    private static long lastCheck;
    private static boolean memoryLow;
    /** The maximum memory the last check used (a test pretending less memory, or ending that, re-checks at once). */
    private static long lastMax;

    private FarDetail() {}

    public static boolean active() {
        if (PerfConfigs.FAR_DETAIL.getBooleanValue() == false || PerfGuard.isOff(PerfGuard.Feature.FAR)) return false;
        long now = System.nanoTime();
        long max = MemoryBudget.maxMemory();
        if (now - lastCheck > 500_000_000L || max != lastMax) {
            lastCheck = now;
            lastMax = max;
            Runtime rt = Runtime.getRuntime();
            memoryLow = PerfMath.javaMemoryLow(max, rt.totalMemory() - rt.freeMemory());
        }
        return memoryLow == false;
    }

    public static boolean isFar(RenderChunkSchematicVbo piece, double camX, double camZ) {
        if (active() == false) return false;
        BlockPos p = piece.getPosition();
        return PerfMath.isFar(p.getX() >> 4, p.getZ() >> 4, (int) Math.floor(camX) >> 4, (int) Math.floor(camZ) >> 4, GameWrap.getRenderDistanceChunks());
    }
}
