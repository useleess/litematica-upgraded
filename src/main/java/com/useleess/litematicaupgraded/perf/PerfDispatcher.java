package com.useleess.litematicaupgraded.perf;

/** Added to Litematica's chunk build dispatcher. */
public interface PerfDispatcher {
    /** How many build threads run (0: builds happen on the render thread). */
    int perf$threadCount();

    int perf$queuedTasks();
}
