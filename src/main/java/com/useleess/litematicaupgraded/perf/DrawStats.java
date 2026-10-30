package com.useleess.litematicaupgraded.perf;

/**
 * What the schematic renderer drew, per frame and in total: draw calls (one per piece and layer, one per batched region,
 * one per overlay piece), vertices, re-sorts. Render thread only.
 */
public final class DrawStats {
    private static long frames;
    private static long drawCalls, blockDraws, vertices, listed, batchedRegions, resorts, farOutlinesSkipped, outlineDraws;
    private static long frameDrawCalls, frameListed;
    /** Vertices drawn per block layer in the last frame (the "same picture" check compares them). */
    private static final long[] layerVertices = new long[4];

    private DrawStats() {}

    /** At the start of a schematic frame. */
    public static void frame() {
        frames++;
        java.util.Arrays.fill(layerVertices, 0);
        frameDrawCalls = 0;
        frameListed = 0;
    }

    public static void draw(int layer, int vertexCount) {
        drawCalls++;
        blockDraws++;
        frameDrawCalls++;
        vertices += vertexCount;
        if (layer >= 0 && layer < 4) layerVertices[layer] += vertexCount;
    }

    public static void overlayDraw(boolean outline) {
        drawCalls++;
        frameDrawCalls++;
        if (outline) outlineDraws++;
    }

    public static void batchedRegion() {
        batchedRegions++;
    }

    public static void listed(int n) {
        listed += n;
        frameListed += n;
    }

    public static void resort() {
        resorts++;
    }

    public static void farOutlineSkipped() {
        farOutlinesSkipped++;
    }

    public static long frames() { return frames; }
    public static long drawCalls() { return drawCalls; }
    public static long blockDraws() { return blockDraws; }
    public static long vertices() { return vertices; }
    public static long listed() { return listed; }
    public static long batchedRegions() { return batchedRegions; }
    public static long resorts() { return resorts; }
    public static long farOutlinesSkipped() { return farOutlinesSkipped; }
    public static long outlineDraws() { return outlineDraws; }
    /** The last frame's numbers (read between frames, e.g. from a client tick). */
    public static long lastFrameListed() { return frameListed; }
    public static long lastFrameDrawCalls() { return frameDrawCalls; }
    public static long[] lastFrameLayerVertices() { return layerVertices.clone(); }
}
