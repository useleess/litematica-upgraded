package com.useleess.litematicaupgraded.perf;

import java.util.Arrays;
import java.util.function.IntPredicate;

/**
 * Splits a vertical block range into the parts that lie in 16-high sections worth visiting (from Litematica Turbo).
 * A schematic column can be hundreds of blocks tall while only a few sections hold blocks.
 */
public final class SectionSlices {
    private SectionSlices() {}

    /**
     * The y ranges to visit as pairs {from0, to0, from1, to1, ...} (inclusive), neighbouring needed sections merged.
     * {@code sectionNeeded} gets the section coordinate (y >> 4). Skipped sections are counted into {@link PerfStats}.
     */
    public static int[] neededRanges(int minY, int maxY, IntPredicate sectionNeeded) {
        if (maxY < minY) return new int[0];
        int first = minY >> 4, last = maxY >> 4;
        int[] out = new int[(last - first + 1) * 2];
        int count = 0, skipped = 0, runStart = Integer.MIN_VALUE;
        for (int s = first; s <= last; ++s) {
            if (sectionNeeded.test(s)) {
                if (runStart == Integer.MIN_VALUE) runStart = Math.max(minY, s << 4);
            } else {
                ++skipped;
                if (runStart != Integer.MIN_VALUE) {
                    out[count++] = runStart;
                    out[count++] = (s << 4) - 1;
                    runStart = Integer.MIN_VALUE;
                }
            }
        }
        if (runStart != Integer.MIN_VALUE) {
            out[count++] = runStart;
            out[count++] = maxY;
        }
        int total = last - first + 1;
        for (int i = 0; i < total; i++) PerfStats.recordSlice(i < skipped);
        return count == out.length ? out : Arrays.copyOf(out, count);
    }
}
