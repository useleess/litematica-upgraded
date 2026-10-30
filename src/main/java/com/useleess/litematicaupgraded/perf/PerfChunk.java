package com.useleess.litematicaupgraded.perf;

import java.util.concurrent.locks.ReentrantLock;

import com.useleess.litematicaupgraded.litematica.render.schematic.RenderGlobalSchematic;

/** Added to Litematica's schematic chunk renderer. */
public interface PerfChunk {
    /** Held while a build or resort of this chunk runs: a cancelled build still finishing never overlaps a new one (they share scratch fields). */
    ReentrantLock perf$buildLock();

    default boolean perf$isBuilding() {
        return perf$buildLock().isLocked();
    }

    /** Cancels a queued or running build, keeping the last finished one. */
    void perf$cancelCompile();

    /** A piece of the complete copy shown while moving: built from every schematic block, without world comparison or overlay. */
    boolean perf$isComplete();

    /**
     * Makes this a complete-copy piece that builds the schematic blocks in these boxes (where they are in the schematic
     * world, which still holds the placement at its place from before the move).
     */
    void perf$makeComplete(java.util.List<com.useleess.litematicaupgraded.malilib.util.position.IntBoundingBox> boxes);

    /** Counts uploads of a block layer on the render thread (a batched region notices that a piece changed). */
    int perf$generation(int layer);

    void perf$noteUpload(int layer, int bytes);

    /** The bytes of block data last uploaded for each layer (the complete copy's memory count). */
    long perf$uploadedBytes();

    /** Counts finished builds (each time the built data is set). */
    int perf$dataGeneration();

    /** Removes this chunk's tile entities from the world renderer. */
    void perf$forgetTileEntities(RenderGlobalSchematic worldRenderer);
}
