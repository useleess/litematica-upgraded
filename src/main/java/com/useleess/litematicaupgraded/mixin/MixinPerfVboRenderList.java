package com.useleess.litematicaupgraded.mixin;

import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.util.BlockRenderLayer;

import com.useleess.litematicaupgraded.litematica.render.schematic.ChunkRenderContainerSchematic;
import com.useleess.litematicaupgraded.litematica.render.schematic.VboRenderListSchematic;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.RenderWrap;
import com.useleess.litematicaupgraded.perf.BatchDraw;
import com.useleess.litematicaupgraded.perf.DrawStats;
import com.useleess.litematicaupgraded.perf.PerfGuard;

/**
 * Draws the schematic's block layers with VBOs: opaque layers region by region (BatchDraw, fewer draw calls), the rest
 * piece by piece exactly as Litematica does; every draw call is counted.
 */
@Mixin(value = VboRenderListSchematic.class, remap = false)
public abstract class MixinPerfVboRenderList extends ChunkRenderContainerSchematic
{
    @Inject(method = "renderChunkLayer", at = @At("HEAD"), cancellable = true, remap = true)
    private void perf$renderChunkLayer(BlockRenderLayer layer, CallbackInfo ci)
    {
        if (this.initialized == false)
        {
            return;
        }

        ci.cancel();
        int index = layer.ordinal();
        boolean batched = false;

        if (layer != BlockRenderLayer.TRANSLUCENT && BatchDraw.active())
        {
            try
            {
                BatchDraw.drawLayer(this, index, this.renderChunks, piece -> this.perf$drawOne(piece, index));
                batched = true;
            }
            catch (RuntimeException e)
            {
                PerfGuard.fail(PerfGuard.Feature.BATCH, e);
            }
        }

        if (batched == false)
        {
            for (RenderChunk renderChunk : this.renderChunks)
            {
                this.perf$drawOne(renderChunk, index);
            }
        }

        RenderWrap.bindBuffer(RenderWrap.GL_ARRAY_BUFFER, 0);
        RenderWrap.resetColor();
        this.renderChunks.clear();
    }

    /** One piece's layer, the way Litematica draws it. */
    @Unique
    private void perf$drawOne(RenderChunk renderChunk, int layer)
    {
        VertexBuffer vertexBuffer = renderChunk.getVertexBufferByLayer(layer);
        GlStateManager.pushMatrix();
        this.preRenderChunk(renderChunk);
        vertexBuffer.bindBuffer();
        BatchDraw.setupBlockPointers();
        vertexBuffer.drawArrays(GL11.GL_QUADS);
        GlStateManager.popMatrix();
        DrawStats.draw(layer, ((IMixinVertexBuffer) vertexBuffer).perf$getCount());
    }
}
