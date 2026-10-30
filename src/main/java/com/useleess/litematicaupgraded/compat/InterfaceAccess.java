package com.useleess.litematicaupgraded.compat;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.ViewFrustum;
import net.minecraft.client.renderer.chunk.RenderChunk;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.util.math.BlockPos;

import com.useleess.litematicaupgraded.litematica.mixin.IMixinViewFrustum;
import com.useleess.litematicaupgraded.malilib.render.buffer.VertexBuilder;
import com.useleess.litematicaupgraded.mixin.IMixinVanillaWrappingVertexBuilder;
import com.useleess.litematicaupgraded.mixin.IMixinVertexBuffer;

/**
 * Casts to our accessor interfaces, kept out of the mixin classes: next to LiteLoader the game runs Mixin 0.7, which cannot
 * resolve an interface mixin used as a type inside another mixin class.
 */
public final class InterfaceAccess {
    private InterfaceAccess() {
    }

    public static RenderChunk renderChunk(ViewFrustum frustum, BlockPos pos) {
        return ((IMixinViewFrustum) frustum).invokeGetRenderChunk(pos);
    }

    public static int vertexCount(VertexBuffer buffer) {
        return ((IMixinVertexBuffer) buffer).perf$getCount();
    }

    public static BufferBuilder bufferOf(VertexBuilder builder) {
        return ((IMixinVanillaWrappingVertexBuilder) builder).perf$getBuffer();
    }
}
