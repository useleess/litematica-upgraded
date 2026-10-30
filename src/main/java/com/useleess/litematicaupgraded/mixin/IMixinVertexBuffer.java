package com.useleess.litematicaupgraded.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.vertex.VertexBuffer;

/** The buffer id and vertex count of a vertex buffer, for batched drawing (BatchDraw) and the complete copy's memory count. */
@Mixin(VertexBuffer.class)
public interface IMixinVertexBuffer
{
    @Accessor("glBufferId")
    int perf$getGlBufferId();

    @Accessor("count")
    int perf$getCount();
}
