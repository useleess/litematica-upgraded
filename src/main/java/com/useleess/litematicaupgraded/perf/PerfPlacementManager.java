package com.useleess.litematicaupgraded.perf;

/** Added to Litematica's placement manager. */
public interface PerfPlacementManager {
    /** Whether a chunk waiting to be copied is one the client has loaded (the others wait until it is). */
    boolean perf$hasCopyableChunks();
}
