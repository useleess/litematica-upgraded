package com.useleess.litematicaupgraded.litematica.render.schematic;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.chunk.CompiledChunk;
import net.minecraft.util.BlockRenderLayer;

import com.useleess.litematicaupgraded.litematica.render.schematic.RenderChunkSchematicVbo.OverlayRenderType;

public class CompiledChunkSchematic extends CompiledChunk
{
    private final boolean[] overlayLayersUsed = new boolean[OverlayRenderType.COUNT];
    private final boolean[] overlayLayersStarted = new boolean[OverlayRenderType.COUNT];
    private final BufferBuilder.State[] blockBufferStates = new BufferBuilder.State[BlockRenderLayer.values().length];
    private final BufferBuilder.State[] overlayBufferStates = new BufferBuilder.State[OverlayRenderType.COUNT];
    /** Bytes of vertex data each block layer, then each overlay type, was built with (the build's signature in the tests). */
    private final int[] builtBytes = new int[BlockRenderLayer.values().length + OverlayRenderType.COUNT];
    private boolean overlayEmpty = true;

    public boolean isOverlayEmpty()
    {
        return this.overlayEmpty;
    }

    protected void setOverlayTypeUsed(OverlayRenderType type)
    {
        this.overlayEmpty = false;
        this.overlayLayersUsed[type.ordinal()] = true;
    }

    public boolean isOverlayTypeEmpty(OverlayRenderType type)
    {
        return this.overlayLayersUsed[type.ordinal()] == false;
    }

    public void setOverlayTypeStarted(OverlayRenderType type)
    {
        this.overlayLayersStarted[type.ordinal()] = true;
    }

    public boolean isOverlayTypeStarted(OverlayRenderType type)
    {
        return this.overlayLayersStarted[type.ordinal()];
    }

    public BufferBuilder.State getBlockBufferState(BlockRenderLayer layer)
    {
        return this.blockBufferStates[layer.ordinal()];
    }

    public void setBlockBufferState(BlockRenderLayer layer, BufferBuilder.State state)
    {
        this.blockBufferStates[layer.ordinal()] = state;
    }

    public BufferBuilder.State getOverlayBufferState(OverlayRenderType type)
    {
        return this.overlayBufferStates[type.ordinal()];
    }

    public void setOverlayBufferState(OverlayRenderType type, BufferBuilder.State state)
    {
        this.overlayBufferStates[type.ordinal()] = state;
    }

    /** Whether it has see-through blocks or overlay quads to re-sort but no copy of their vertices (#19). */
    public boolean lacksSortCopy()
    {
        return (this.isLayerEmpty(BlockRenderLayer.TRANSLUCENT) == false && this.getBlockBufferState(BlockRenderLayer.TRANSLUCENT) == null)
            || (this.isOverlayTypeEmpty(OverlayRenderType.QUAD) == false && this.getOverlayBufferState(OverlayRenderType.QUAD) == null);
    }

    public void setBuiltBytes(BlockRenderLayer layer, int bytes)
    {
        this.builtBytes[layer.ordinal()] = bytes;
    }

    public void setBuiltBytes(OverlayRenderType type, int bytes)
    {
        this.builtBytes[BlockRenderLayer.values().length + type.ordinal()] = bytes;
    }

    public int[] getBuiltBytes()
    {
        return this.builtBytes.clone();
    }

    /** Lets go of the re-sort copies (a piece far from the camera is never re-sorted). */
    public void dropSortCopies()
    {
        java.util.Arrays.fill(this.blockBufferStates, null);
        java.util.Arrays.fill(this.overlayBufferStates, null);
    }

    public static final CompiledChunkSchematic EMPTY = new CompiledChunkSchematic() {
        @Override
        public void setLayerUsed(BlockRenderLayer layer)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setLayerStarted(BlockRenderLayer layer)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setOverlayTypeUsed(OverlayRenderType layer)
        {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setOverlayTypeStarted(OverlayRenderType layer)
        {
            throw new UnsupportedOperationException();
        }
    };
}
