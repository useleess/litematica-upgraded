package com.useleess.litematicaupgraded.preview;

import java.util.Arrays;
import java.util.function.Predicate;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

/**
 * A schematic shrunk to a smaller one: one block per cell of {@code unit} x {@code unit} x {@code unit} source blocks. A cell shows its
 * most common block (air never counts); a tie goes to an opaque block, so a shrunk wall has no holes, and otherwise to the block seen
 * first. Only cells that hold a block are stored, each with a small tally of the blocks in it.
 */
final class DownsampledGrid<S> {
    /** Source blocks per cell edge. */
    final int unit;
    /** Size in cells. */
    final int sizeX, sizeY, sizeZ;
    private final Long2ObjectOpenHashMap<Tally<S>> cells = new Long2ObjectOpenHashMap<>();
    private final Predicate<S> opaque;

    /** A grid for a source box of this size (in blocks). */
    DownsampledGrid(int sourceX, int sourceY, int sourceZ, int unit, Predicate<S> opaque) {
        this.unit = unit;
        this.sizeX = cellsOf(sourceX, unit);
        this.sizeY = cellsOf(sourceY, unit);
        this.sizeZ = cellsOf(sourceZ, unit);
        this.opaque = opaque;
    }

    private static int cellsOf(int blocks, int unit) {
        return Math.max(1, (blocks + unit - 1) / unit);
    }

    /** A source block (not air) at a position from 0 in the source box. */
    void offer(int x, int y, int z, S state) {
        add(BlockSelection.pack(x / unit, y / unit, z / unit), state, 1);
    }

    private void add(long key, S state, int times) {
        Tally<S> tally = cells.get(key);
        if (tally == null) cells.put(key, tally = new Tally<>());
        tally.add(state, times);
    }

    /** The block of a cell, null for air. */
    S get(int cellX, int cellY, int cellZ) {
        Tally<S> tally = cells.get(BlockSelection.pack(cellX, cellY, cellZ));
        return tally == null ? null : tally.winner(opaque);
    }

    int count() {
        return cells.size();
    }

    /** The same build with cells {@code factor} times wider. */
    DownsampledGrid<S> coarser(int factor) {
        DownsampledGrid<S> next = new DownsampledGrid<>(sizeX * unit, sizeY * unit, sizeZ * unit, unit * factor, opaque);
        for (Long2ObjectMap.Entry<Tally<S>> e : cells.long2ObjectEntrySet()) {
            long key = e.getLongKey();
            long target = BlockSelection.pack(BlockSelection.x(key) * unit / next.unit, BlockSelection.y(key) * unit / next.unit,
                    BlockSelection.z(key) * unit / next.unit);
            Tally<S> into = next.cells.get(target);
            if (into == null) next.cells.put(target, into = new Tally<>());
            e.getValue().addTo(into);
        }
        return next;
    }

    /** Every filled cell as a packed position (see {@link BlockSelection}). */
    void forEachCell(CellVisitor<S> visitor) {
        for (Long2ObjectMap.Entry<Tally<S>> e : cells.long2ObjectEntrySet()) visitor.cell(e.getLongKey(), e.getValue().winner(opaque));
    }

    interface CellVisitor<S> {
        void cell(long packed, S state);
    }

    /** The distinct blocks of one cell with how often each was seen, in the order first seen. */
    private static final class Tally<S> {
        Object[] states = new Object[2];
        int[] counts = new int[2];
        int size;

        void add(S state, int times) {
            for (int i = 0; i < size; i++) {
                if (states[i].equals(state)) {
                    counts[i] += times;
                    return;
                }
            }
            if (size == states.length) {
                states = Arrays.copyOf(states, size * 2);
                counts = Arrays.copyOf(counts, size * 2);
            }
            states[size] = state;
            counts[size++] = times;
        }

        @SuppressWarnings("unchecked")
        void addTo(Tally<S> other) {
            for (int i = 0; i < size; i++) other.add((S) states[i], counts[i]);
        }

        @SuppressWarnings("unchecked")
        S winner(Predicate<S> opaque) {
            int best = 0;
            for (int i = 1; i < size; i++) {
                if (counts[i] > counts[best] || counts[i] == counts[best] && !opaque.test((S) states[best]) && opaque.test((S) states[i])) best = i;
            }
            return (S) states[best];
        }
    }
}
