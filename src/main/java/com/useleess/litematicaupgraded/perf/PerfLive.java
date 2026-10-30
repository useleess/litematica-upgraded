package com.useleess.litematicaupgraded.perf;

/** Changes that must be applied at a safe moment (the start of a render frame). */
public final class PerfLive {
    private static volatile boolean reload;

    private PerfLive() {}

    public static void requestReload() {
        reload = true;
    }

    /** True once after a request. */
    public static boolean consumeReload() {
        if (!reload) return false;
        reload = false;
        return true;
    }
}
