package com.useleess.litematicaupgraded.preview;

import java.util.function.BooleanSupplier;

/** Visits the blocks of one region (a box) that are not air, and packs positions into a long. */
public final class BlockSelection {
    /** A box of blocks. */
    public interface Grid {
        int sizeX();

        int sizeY();

        int sizeZ();

        boolean solid(int x, int y, int z);
    }

    private BlockSelection() {}

    /** Gets each block that is not air. */
    public interface SolidVisitor {
        void solid(int x, int y, int z);
    }

    /** Calls the visitor for every block of the region that is not air; stops early once {@code cancelled} says so. */
    public static void forEachSolid(Grid g, BooleanSupplier cancelled, SolidVisitor visitor) {
        for (int y = 0; y < g.sizeY(); y++) {
            if (cancelled.getAsBoolean()) return;
            for (int z = 0; z < g.sizeZ(); z++) {
                for (int x = 0; x < g.sizeX(); x++) {
                    if (g.solid(x, y, z)) visitor.solid(x, y, z);
                }
            }
        }
    }

    public static long pack(int x, int y, int z) {
        return (long) (x & 0x1FFFFF) << 42 | (long) (y & 0x1FFFFF) << 21 | (z & 0x1FFFFF);
    }

    public static int x(long packed) {
        return (int) (packed >>> 42) & 0x1FFFFF;
    }

    public static int y(long packed) {
        return (int) (packed >>> 21) & 0x1FFFFF;
    }

    public static int z(long packed) {
        return (int) packed & 0x1FFFFF;
    }
}
