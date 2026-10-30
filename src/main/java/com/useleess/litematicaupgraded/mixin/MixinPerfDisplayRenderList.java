package com.useleess.litematicaupgraded.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.util.BlockRenderLayer;

import com.useleess.litematicaupgraded.litematica.render.schematic.ChunkRenderContainerSchematic;
import com.useleess.litematicaupgraded.litematica.render.schematic.RenderListSchematic;
import com.useleess.litematicaupgraded.perf.DrawStats;

/** With "Use VBOs" off: counts the display lists drawn (one draw call per piece and layer). */
@Mixin(value = RenderListSchematic.class, remap = false)
public abstract class MixinPerfDisplayRenderList extends ChunkRenderContainerSchematic
{
    @Inject(method = "renderChunkLayer", at = @At("HEAD"), remap = true)
    private void perf$countLists(BlockRenderLayer layer, CallbackInfo ci)
    {
        if (this.initialized)
        {
            for (RenderChunk renderChunk : this.renderChunks)
            {
                DrawStats.draw(layer.ordinal(), 0);
            }
        }
    }
}
