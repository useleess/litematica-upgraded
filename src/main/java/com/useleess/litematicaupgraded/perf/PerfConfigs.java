package com.useleess.litematicaupgraded.perf;

import com.google.common.collect.ImmutableList;

import com.useleess.litematicaupgraded.malilib.config.option.BooleanConfig;
import com.useleess.litematicaupgraded.malilib.config.option.ConfigOption;
import com.useleess.litematicaupgraded.malilib.config.option.IntegerConfig;

/**
 * The speed settings, in the "Litematica Upgraded" tab (PrinterConfigs adds them to the tab's list). Every speed-up is on
 * by default (but batch drawing, see there) and read live by the code it switches, so a benchmark can compare off and on in one run.
 * Ideas and tuning from Litematica Turbo (Useleess); sparse storage from Schematica Upgraded (MIT: Lunatrius / HackerRouter / Useleess).
 */
public final class PerfConfigs {
    /** Schematic chunks are built on background threads, also the ones next to the camera (no builds on the render thread). */
    public static final BooleanConfig THREADED_BUILD = new BooleanConfig("perfThreadedBuild", true);
    /** 0 = automatic (the CPU count, limited by memory). Changing it makes the build threads again. */
    public static final IntegerConfig BUILD_THREADS = new IntegerConfig("perfBuildThreads", 0, 0, 16);
    public static final BooleanConfig SKIP_EMPTY_SLICES = new BooleanConfig("perfSkipEmptySlices", true);
    public static final BooleanConfig FAST_COPY = new BooleanConfig("perfFastCopy", true);
    public static final BooleanConfig GHOST_MOVE = new BooleanConfig("perfGhostMove", true);
    public static final BooleanConfig SPARSE_STORAGE = new BooleanConfig("perfSparseStorage", true);
    public static final BooleanConfig SHOW_STATS = new BooleanConfig("perfShowStats", true);
    /**
     * Built pieces are grouped into bigger draw jobs (fewer draw calls, same picture). Off by default: measured in game it
     * saved no frame time, and making a group reads the pieces back from the graphics card, which stalls frames while a
     * schematic is being built.
     */
    public static final BooleanConfig BATCH_DRAW = new BooleanConfig("perfBatchDraw", false);
    /** See-through blocks are re-sorted only after the camera moved a real distance. */
    public static final BooleanConfig LAZY_TRANSLUCENT_SORT = new BooleanConfig("perfLazyTranslucentSort", true);
    /** Automatic build thread count = CPU cores - 1 (at least 1): one core is always left for the game. */
    public static final BooleanConfig AUTO_WORKERS = new BooleanConfig("perfAutoWorkers", true);
    /** Far pieces are drawn with less detail (no outline lines); off by itself on low-memory PCs. */
    public static final BooleanConfig FAR_DETAIL = new BooleanConfig("perfFarDetail", true);
    /** While a placement moves, a complete copy (every block, no world comparison) is drawn so matching blocks stay visible. */
    public static final BooleanConfig MOVE_FULL_COPY = new BooleanConfig("perfMoveFullCopy", true);
    /** Memory limit of the complete copy in MB; 0 = automatic (a quarter of the free graphics memory, or 128 MB). */
    public static final IntegerConfig FULL_COPY_BUDGET_MB = new IntegerConfig("perfFullCopyBudgetMb", 0, 0, 4096);

    public static final ImmutableList<ConfigOption<?>> OPTIONS = ImmutableList.of(
            THREADED_BUILD,
            BUILD_THREADS,
            SKIP_EMPTY_SLICES,
            FAST_COPY,
            GHOST_MOVE,
            SPARSE_STORAGE,
            SHOW_STATS,
            BATCH_DRAW,
            LAZY_TRANSLUCENT_SORT,
            AUTO_WORKERS,
            FAR_DETAIL,
            MOVE_FULL_COPY,
            FULL_COPY_BUDGET_MB
    );

    private static boolean wired;

    private PerfConfigs() {}

    /** Called when malilib initialises: the settings that need the build threads made again ask for it. */
    public static void wire() {
        if (wired) return;
        wired = true;
        BUILD_THREADS.setValueChangeCallback((n, o) -> PerfLive.requestReload());
        THREADED_BUILD.setValueChangeCallback((n, o) -> PerfLive.requestReload());
        AUTO_WORKERS.setValueChangeCallback((n, o) -> PerfLive.requestReload());
    }
}
