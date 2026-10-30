package com.useleess.litematicaupgraded.perf;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Counters for the schematic chunk builds, shown in F3 and read by the benchmark. Thread safe. */
public final class PerfStats {
    private static final AtomicLong builds = new AtomicLong();
    private static final AtomicLong buildNanos = new AtomicLong();
    private static final AtomicLong mainBuilds = new AtomicLong();
    private static final AtomicLong mainNanos = new AtomicLong();
    private static final AtomicLong slicesScanned = new AtomicLong();
    private static final AtomicLong slicesSkipped = new AtomicLong();
    private static final AtomicLong copyCalls = new AtomicLong();
    private static final AtomicLong copyFast = new AtomicLong();
    private static final AtomicLong copyCellsSkipped = new AtomicLong();
    private static final AtomicLong copyNanos = new AtomicLong();
    private static final AtomicLong ghostMoves = new AtomicLong();
    private static final AtomicLong completeBuilds = new AtomicLong();

    /** What each chunk build produced (vertex counts of its layers), by sub-chunk position and where it ran, for comparing builds. */
    private static final Map<Long, int[]> SIGNATURES_WORKER = new ConcurrentHashMap<>();
    private static final Map<Long, int[]> SIGNATURES_MAIN = new ConcurrentHashMap<>();

    private static long windowStart = System.nanoTime();
    private static long windowBuilds, windowNanos, windowMainNanos;
    private static String summary = "";

    private PerfStats() {}

    public static void recordBuild(long nanos, boolean onMainThread) {
        builds.incrementAndGet();
        buildNanos.addAndGet(nanos);
        if (onMainThread) {
            mainBuilds.incrementAndGet();
            mainNanos.addAndGet(nanos);
        }
    }

    public static void recordSlice(boolean skipped) {
        slicesScanned.incrementAndGet();
        if (skipped) slicesSkipped.incrementAndGet();
    }

    public static void recordCopy(boolean fast, long cellsSkipped, long nanos) {
        copyCalls.incrementAndGet();
        if (fast) copyFast.incrementAndGet();
        copyCellsSkipped.addAndGet(cellsSkipped);
        copyNanos.addAndGet(nanos);
    }

    public static void recordCompleteBuild() {
        completeBuilds.incrementAndGet();
    }

    public static long completeBuilds() { return completeBuilds.get(); }

    public static void recordGhostMove() {
        ghostMoves.incrementAndGet();
    }

    public static void recordSignature(long chunkKey, int[] layerVertexCounts, boolean onMainThread) {
        (onMainThread ? SIGNATURES_MAIN : SIGNATURES_WORKER).put(chunkKey, layerVertexCounts);
    }

    public static Map<Long, int[]> signatures(boolean mainThread) {
        return mainThread ? SIGNATURES_MAIN : SIGNATURES_WORKER;
    }

    public static void clearSignatures() {
        SIGNATURES_MAIN.clear();
        SIGNATURES_WORKER.clear();
    }

    public static long builds() { return builds.get(); }
    public static long buildNanos() { return buildNanos.get(); }
    public static long mainBuilds() { return mainBuilds.get(); }
    public static long mainNanos() { return mainNanos.get(); }
    public static long slicesScanned() { return slicesScanned.get(); }
    public static long slicesSkipped() { return slicesSkipped.get(); }
    public static long copyCalls() { return copyCalls.get(); }
    public static long copyFast() { return copyFast.get(); }
    public static long copyCellsSkipped() { return copyCellsSkipped.get(); }
    public static long copyNanos() { return copyNanos.get(); }
    public static long ghostMoves() { return ghostMoves.get(); }

    /** One line over the last second for the F3 screen. */
    public static synchronized String summary(int threads, int queued) {
        long now = System.nanoTime();
        long elapsed = now - windowStart;
        if (elapsed >= 1_000_000_000L || summary.isEmpty()) {
            long b = builds.get(), bn = buildNanos.get(), mn = mainNanos.get();
            long db = b - windowBuilds;
            double seconds = Math.max(elapsed, 1) / 1.0e9;
            double avgMs = db > 0 ? (bn - windowNanos) / 1.0e6 / db : 0.0;
            double mainMsPerSec = (mn - windowMainNanos) / 1.0e6 / seconds;
            long scanned = slicesScanned.get(), skipped = slicesSkipped.get();
            int skipPct = scanned > 0 ? (int) (skipped * 100 / scanned) : 0;
            summary = String.format("Perf T:%d Q:%d B/s:%.0f avg:%.1fms main:%.0fms/s skip:%d%%", threads, queued, db / seconds, avgMs, mainMsPerSec, skipPct);
            windowStart = now;
            windowBuilds = b;
            windowNanos = bn;
            windowMainNanos = mn;
        }
        return summary;
    }
}
