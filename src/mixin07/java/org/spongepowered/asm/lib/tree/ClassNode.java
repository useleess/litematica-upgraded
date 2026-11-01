package org.spongepowered.asm.lib.tree;

/**
 * Compile-time stand-in, NOT part of any jar. Mixin 0.7 (the one LiteLoader brings) shades ASM as org.spongepowered.asm.lib,
 * so its IMixinConfigPlugin.preApply / postApply take this ClassNode; our plugin answers both signatures (see CompatMixinPlugin).
 */
public class ClassNode {
}
