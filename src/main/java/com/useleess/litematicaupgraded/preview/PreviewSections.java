package com.useleess.litematicaupgraded.preview;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

import it.unimi.dsi.fastutil.longs.LongArrayList;

/** Splits a shrunk schematic into sections of {@link PreviewMath#SECTION} cells on each edge, so they can be built side by side. */
final class PreviewSections {
    private PreviewSections() {}

    /** The packed cell positions of each section that holds blocks, lowest layer first; every filled cell is in exactly one list. */
    static <S> List<LongArrayList> split(DownsampledGrid<S> grid) {
        LongArrayList cells = new LongArrayList(grid.count());
        grid.forEachCell((packed, state) -> cells.add(packed));
        return split(cells);
    }

    /** The same for plain packed positions. */
    static List<LongArrayList> split(LongArrayList positions) {
        TreeMap<Long, LongArrayList> sections = new TreeMap<>();
        int shift = Integer.numberOfTrailingZeros(PreviewMath.SECTION);
        for (int i = 0; i < positions.size(); i++) {
            long packed = positions.getLong(i);
            long key = BlockSelection.pack(BlockSelection.y(packed) >> shift, BlockSelection.x(packed) >> shift, BlockSelection.z(packed) >> shift);
            sections.computeIfAbsent(key, k -> new LongArrayList()).add(packed);
        }
        return new ArrayList<>(sections.values());
    }
}
