package com.useleess.litematicaupgraded.perf;

import java.util.NoSuchElementException;

/**
 * Walks the cells of a box but only inside the given y ranges, in the order of the plain loops (x fastest, then y, then z),
 * so the built vertex data keeps its order (from Litematica Turbo).
 */
public final class BoxCellIterator {
    private final int minX, maxX, minZ, maxZ;
    private final int[] yRanges;
    private int range, x, y, z;
    private boolean hasNext;

    public BoxCellIterator(int minX, int minZ, int maxX, int maxZ, int[] yRanges) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.yRanges = yRanges;
        this.hasNext = yRanges.length >= 2 && maxX >= minX && maxZ >= minZ;
        if (hasNext) {
            x = minX;
            z = minZ;
            y = yRanges[0];
        }
    }

    public boolean hasNext() {
        return hasNext;
    }

    /** Writes the current cell into {@code outXyz} and moves on. */
    public void advance(int[] outXyz) {
        if (!hasNext) throw new NoSuchElementException();
        outXyz[0] = x;
        outXyz[1] = y;
        outXyz[2] = z;
        if (++x > maxX) {
            x = minX;
            if (++y > yRanges[range * 2 + 1]) {
                ++range;
                if (range * 2 >= yRanges.length) {
                    range = 0;
                    if (++z > maxZ) {
                        hasNext = false;
                        return;
                    }
                }
                y = yRanges[range * 2];
            }
        }
    }
}
