package com.useleess.litematicaupgraded.perf;

/** The pure rules of the draw-side speed-ups (no game classes, unit tested). */
public final class PerfMath {
    /** Fixed limit of the complete copy when the graphics card cannot report its free memory. */
    public static final long FIXED_BUDGET_BYTES = 128L * 1024 * 1024;
    /** Re-sort see-through blocks only after the camera moved this far (blocks) ... */
    public static final double RESORT_DISTANCE = 2.0;
    /** ... and at most this often. */
    public static final long RESORT_MIN_NANOS = 250_000_000L;
    /** A batch region is this many pieces wide in each direction (4 x 4 x 4 pieces = 64 blocks). */
    public static final int REGION_SHIFT = 2;
    /** Bytes per vertex of Minecraft's block format (position 3 floats, colour, uv, light uv). */
    public static final int BLOCK_VERTEX_BYTES = 28;

    private PerfMath() {}

    /**
     * Build workers: CPU cores - 1, at least 1, so one core is always left for the game; never more than Litematica's own
     * memory limit ({@link #memoryThreadCap}), since every worker holds build buffers.
     */
    public static int workers(int cores, int memoryCap) {
        return Math.max(1, Math.min(cores - 1, memoryCap));
    }

    /** The most build threads Litematica allows for this much Java memory (its own rule: 15 % of it, 50 MB per thread). */
    public static int memoryThreadCap(long maxMemoryBytes) {
        int limit = Math.max(1, (int) ((double) maxMemoryBytes * 0.15D) / 10485760);
        return Math.max(1, limit / 5);
    }

    /**
     * The complete copy's memory limit in bytes: the slider when set (MB), else a quarter of the free graphics memory the
     * card reports (NVX, then ATI, in KB; below 0 = not reported), else the fixed 128 MB.
     */
    public static long copyBudgetBytes(int sliderMb, long nvxFreeKb, long atiFreeKb) {
        if (sliderMb > 0) return sliderMb * 1024L * 1024L;
        if (nvxFreeKb > 0) return nvxFreeKb * 1024L / 4;
        if (atiFreeKb > 0) return atiFreeKb * 1024L / 4;
        return FIXED_BUDGET_BYTES;
    }

    /** Where the automatic limit came from: "slider", "nvx", "ati" or "fixed". */
    public static String copyBudgetSource(int sliderMb, long nvxFreeKb, long atiFreeKb) {
        if (sliderMb > 0) return "slider";
        if (nvxFreeKb > 0) return "nvx";
        if (atiFreeKb > 0) return "ati";
        return "fixed";
    }

    /** Java memory is low (less than a quarter of the maximum free): the complete copy is skipped / dropped and far detail is off. */
    public static boolean javaMemoryLow(long maxBytes, long usedBytes) {
        return maxBytes - usedBytes < maxBytes / 4;
    }

    /** Whether see-through blocks are re-sorted now (NaN distances never pass: a paused sort stays paused). */
    public static boolean shouldResort(double distSq, long nanosSinceLast) {
        return distSq >= RESORT_DISTANCE * RESORT_DISTANCE && nanosSinceLast >= RESORT_MIN_NANOS;
    }

    /** The stock rule: re-sort as soon as the camera moved more than one block. */
    public static boolean stockResort(double distSq) {
        return distSq > 1.0;
    }

    /** Pieces at least this many pieces away (horizontally) count as far: half the render distance, at least 3. */
    public static int farPieces(int renderDistanceChunks) {
        return Math.max(3, renderDistanceChunks / 2);
    }

    public static boolean isFar(int pieceX, int pieceZ, int cameraPieceX, int cameraPieceZ, int renderDistanceChunks) {
        int d = Math.max(Math.abs(pieceX - cameraPieceX), Math.abs(pieceZ - cameraPieceZ));
        return d >= farPieces(renderDistanceChunks);
    }

    /** The batch region of a piece (piece coordinates = block >> 4), packed into one long. */
    public static long regionKey(int pieceX, int pieceY, int pieceZ) {
        long rx = pieceX >> REGION_SHIFT, ry = pieceY >> REGION_SHIFT, rz = pieceZ >> REGION_SHIFT;
        return ((rx & 0x3FFFFFL) << 42) | ((rz & 0x3FFFFFL) << 20) | (ry & 0xFFFFFL);
    }

    /** The block position of a region's corner along one axis, from a piece coordinate. */
    public static int regionOrigin(int pieceCoord) {
        return (pieceCoord >> REGION_SHIFT) << (REGION_SHIFT + 4);
    }

    /**
     * Moves the positions of block vertices (relative to their piece) so they are relative to the region: adds the piece's
     * offset inside the region to each vertex's x, y, z (the first three floats of each 28-byte vertex).
     */
    public static void shiftPositions(java.nio.ByteBuffer data, int start, int vertices, float dx, float dy, float dz) {
        for (int i = 0; i < vertices; i++) {
            int at = start + i * BLOCK_VERTEX_BYTES;
            data.putFloat(at, data.getFloat(at) + dx);
            data.putFloat(at + 4, data.getFloat(at + 4) + dy);
            data.putFloat(at + 8, data.getFloat(at + 8) + dz);
        }
    }

    /** Whether a complete-copy piece of this many bytes may be kept: it must fit in what is left of the limit. */
    public static boolean fitsBudget(long usedBytes, long pieceBytes, long budgetBytes) {
        return usedBytes + pieceBytes <= budgetBytes;
    }
}
