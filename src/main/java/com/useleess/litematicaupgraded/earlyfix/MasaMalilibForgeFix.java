package com.useleess.litematicaupgraded.earlyfix;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.MixinEnvironment;

import net.minecraft.launchwrapper.IClassTransformer;

/**
 * masa's LiteLoader malilib in a Forge + LiteLoader game: its hook after a screen is drawn targets GuiScreen.drawScreen in
 * EntityRenderer.updateCameraAndRender, a call Forge replaces with ForgeHooksClient.drawScreen, so his Mixin stops the game
 * at start-up even without our mod. Litematica Upgraded only replaces Litematica - players keep malilib for MiniHUD,
 * Tweakeroo, Item Scroller (Discord #2) - so while Mixin reads that one mixin class its target is moved to Forge's call, the
 * change our own copy of malilib has (malilib.mixin.render.EntityRendererMixin), and made optional. Registered by {@link MasaMalilibForgeFixTweaker} (litemod.json
 * tweakClass): only LiteLoader games run it. Anything unexpected leaves his class as it was and logs why.
 */
public final class MasaMalilibForgeFix implements IClassTransformer {
    static final String MIXIN = "fi.dy.masa.malilib.mixin.render.EntityRendererMixin";
    static final String VANILLA = "Lnet/minecraft/client/gui/GuiScreen;drawScreen(IIF)V";
    static final String FORGE = "Lnet/minecraftforge/client/ForgeHooksClient;drawScreen(Lnet/minecraft/client/gui/GuiScreen;IIF)V";
    private static final Logger LOGGER = LogManager.getLogger("Litematica Upgraded");

    public MasaMalilibForgeFix() {
        // Mixin reads mixin classes through a list of transformers it builds once - before LiteLoader adds this one. Adding an
        // exclusion (a name no transformer has) makes it build the list again, with this transformer in it.
        try {
            MixinEnvironment.getCurrentEnvironment().addTransformerExclusion(MasaMalilibForgeFix.class.getName() + "$None");
        } catch (Throwable e) {
            LOGGER.error("masa's malilib: could not join Mixin's transformer list, his screen hook stays as it is", e);
        }
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] bytes) {
        if (bytes == null || !MIXIN.equals(transformedName) || !forgePresent()) return bytes;
        try {
            byte[] fixed = retarget(bytes);
            LOGGER.info(fixed == bytes ? "masa's malilib: no screen hook on GuiScreen.drawScreen, left as it is"
                                       : "masa's malilib: screen hook moved to Forge's ForgeHooksClient.drawScreen (it stops the game on Forge)");
            return fixed;
        } catch (RuntimeException e) {
            LOGGER.error("masa's malilib: could not move its screen hook to Forge's call, left as it is", e);
            return bytes;
        }
    }

    private static boolean forgePresent() {
        return MasaMalilibForgeFix.class.getClassLoader().getResource("net/minecraftforge/client/ForgeHooksClient.class") != null;
    }

    /** The class with every annotation value VANILLA replaced by FORGE; the same array when it has none. */
    static byte[] retarget(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            changed |= retargetHooks(method.visibleAnnotations) | retargetHooks(method.invisibleAnnotations);
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    /**
     * Moves the hooks among these annotations; a moved one also becomes optional (require = 0), like our own copy's with
     * OptiFine, which rewrites Forge's call too: skipped there instead of stopping the game.
     */
    private static boolean retargetHooks(List<AnnotationNode> hooks) {
        if (hooks == null) return false;
        boolean changed = false;
        for (AnnotationNode hook : hooks) {
            if (!retarget(hook.values)) continue;
            int require = hook.values.indexOf("require");
            if (require >= 0 && require % 2 == 0) hook.values.set(require + 1, 0);
            else {
                hook.values.add("require");
                hook.values.add(0);
            }
            changed = true;
        }
        return changed;
    }

    @SuppressWarnings("unchecked")
    private static boolean retarget(List<?> values) {
        if (values == null) return false;
        boolean changed = false;
        List<Object> list = (List<Object>) values;
        for (int i = 0; i < list.size(); i++) {
            Object value = list.get(i);
            if (VANILLA.equals(value)) {
                list.set(i, FORGE);
                changed = true;
            } else if (value instanceof AnnotationNode) {
                changed |= retarget(((AnnotationNode) value).values);
            } else if (value instanceof List) {
                changed |= retarget((List<?>) value);
            }
        }
        return changed;
    }
}
