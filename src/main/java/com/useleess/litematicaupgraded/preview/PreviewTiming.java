package com.useleess.litematicaupgraded.preview;

import com.useleess.litematicaupgraded.litematica.Litematica;

/**
 * How long each stage of one preview took, in milliseconds (-1 = not reached yet): reading the file, shrinking it, building the vertex data
 * and turning it into display lists are lengths of time; the first picture and the complete model are counted from the moment the file
 * was selected. Logged once per preview.
 */
public final class PreviewTiming {
    public volatile long readMs = -1, downsampleMs = -1, buildMs = -1, uploadMs = -1, firstFrameMs = -1, completeMs = -1;
    /** Full (1:1) model of a shrunk file: build time, upload time, blocks, vertices; -1 = not built (yet, or over the size cap). */
    public volatile long detailBuildMs = -1, detailUploadMs = -1, detailBlocks = -1, detailVertices = -1;
    private volatile boolean detailLogged;
    /** The first picture was the rough copy of a big file. */
    public volatile boolean rough;
    /** The model was kept from an earlier look at the file. */
    public volatile boolean fromCache;
    private final long startNanos = System.nanoTime();
    private volatile boolean logged;

    /** Milliseconds since the file was selected. */
    long elapsedMs() {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /** Logs the full model's numbers once, when it is complete. */
    void logDetailOnce(String file) {
        if (detailLogged) return;
        detailLogged = true;
        Litematica.LOGGER.info("PERF preview-detail {}: build {} ms, upload {} ms, {} blocks, {} vertices", file, detailBuildMs, detailUploadMs, detailBlocks,
                detailVertices);
    }

    /** Logs the numbers once, when the model is complete. */
    void logOnce(String file, long blocks, int step) {
        if (logged) return;
        logged = true;
        Litematica.LOGGER.info("PERF preview {}{}: read {} ms, shrink {} ms, build {} ms, upload {} ms, first picture {} ms{}, complete {} ms, {} blocks, 1:{}",
                file, fromCache ? " (kept)" : "", readMs, downsampleMs, buildMs, uploadMs, firstFrameMs, rough ? " (rough)" : "", completeMs, blocks, step);
    }
}
