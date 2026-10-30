package com.useleess.litematicaupgraded.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.client.renderer.BufferBuilder;

import com.useleess.litematicaupgraded.malilib.render.buffer.VanillaWrappingVertexBuilder;

/** The game's buffer inside malilib's vertex builder (the display-list upload draws it). */
@Mixin(value = VanillaWrappingVertexBuilder.class, remap = false)
public interface IMixinVanillaWrappingVertexBuilder
{
    @Accessor("buffer")
    BufferBuilder perf$getBuffer();
}
