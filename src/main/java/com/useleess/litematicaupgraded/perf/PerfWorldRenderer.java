package com.useleess.litematicaupgraded.perf;

import java.util.List;
import javax.annotation.Nullable;

import com.useleess.litematicaupgraded.litematica.render.schematic.RenderChunkSchematicVbo;

/** Added to Litematica's schematic world renderer. */
public interface PerfWorldRenderer {
    /** True when no schematic chunk is waiting to be built or uploaded. */
    boolean perf$isIdle();

    /** Takes the chunk renderers that hold built buffers out of the renderer (they keep their buffers); fresh ones take their places. Null when there is no renderer yet. */
    @Nullable
    List<RenderChunkSchematicVbo> perf$detachChunkRenderers();

    /** Throws the build threads away and makes new ones (the thread count changed). */
    void perf$resetDispatcher();

    /** A new chunk renderer (not part of the view) at this place, for the complete copy. */
    RenderChunkSchematicVbo perf$newRenderer(int x, int y, int z);

    @Nullable
    com.useleess.litematicaupgraded.litematica.render.schematic.ChunkRenderDispatcherLitematica perf$dispatcher();

    /** What the last frame drew (after the move swap). */
    List<RenderChunkSchematicVbo> perf$lastDrawList();

    /** The chunk renderers listed for drawing (in view, in range). */
    List<RenderChunkSchematicVbo> perf$listedRenderers();

    /** How many build threads run now (0 when there is no dispatcher or builds happen on the render thread). */
    int perf$buildThreads();

    /** Whether the chunk renderer holding this block has built block models (an empty or never built chunk answers false). */
    boolean perf$chunkHasModels(int x, int y, int z);

    /** A short description of the chunk renderer holding this block, for failure messages. */
    String perf$describeChunk(int x, int y, int z);

    /** Sub-chunks the renderer lists for drawing now (ghosts excluded) whose build made block models (the overlay is not counted). */
    int perf$renderedWithData();
}
