package com.useleess.litematicaupgraded.earlyfix;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** masa's malilib screen hook is moved to Forge's call; anything else is left exactly as it was (Discord #2). */
public class MasaMalilibForgeFixTest {
    private final MasaMalilibForgeFix fix = new MasaMalilibForgeFix();

    @Test
    public void screenHookMovesToForgesCall() {
        String classText = text(fix.transform(MasaMalilibForgeFix.MIXIN, MasaMalilibForgeFix.MIXIN, mixinWithTarget(MasaMalilibForgeFix.VANILLA)));
        assertTrue("the hook does not target Forge's call", classText.contains(MasaMalilibForgeFix.FORGE));
        assertFalse("the vanilla target is still there", classText.contains(MasaMalilibForgeFix.VANILLA));
    }

    @Test
    public void movedHookIsOptional() {
        org.objectweb.asm.tree.ClassNode node = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(fix.transform(MasaMalilibForgeFix.MIXIN, MasaMalilibForgeFix.MIXIN, mixinWithTarget(MasaMalilibForgeFix.VANILLA))).accept(node, 0);
        List<Object> inject = node.methods.get(0).invisibleAnnotations.get(0).values;
        assertEquals("the moved hook is not optional (require = 0)", 0, inject.get(inject.indexOf("require") + 1));
    }

    @Test
    public void otherHooksAndClassesStayTheSame() {
        byte[] otherHook = mixinWithTarget("Lnet/minecraft/client/gui/GuiIngame;renderGameOverlay(F)V");
        assertSame(otherHook, fix.transform(MasaMalilibForgeFix.MIXIN, MasaMalilibForgeFix.MIXIN, otherHook));
        byte[] otherClass = mixinWithTarget(MasaMalilibForgeFix.VANILLA);
        assertSame(otherClass, fix.transform("x", "fi.dy.masa.malilib.mixin.render.SomethingElse", otherClass));
    }

    @Test
    public void unreadableClassIsLeftAsItWas() {
        byte[] broken = {1, 2, 3};
        assertArrayEquals(broken, fix.transform(MasaMalilibForgeFix.MIXIN, MasaMalilibForgeFix.MIXIN, broken));
    }

    @Test
    public void tweakerPackageHoldsOnlyTheEarlyFix() {
        // the launcher loads a tweaker's whole package outside the game: any other class here sees the game without our mixins
        String[] files = new java.io.File("src/main/java/com/useleess/litematicaupgraded/earlyfix").list();
        java.util.Arrays.sort(files);
        assertArrayEquals(new String[] {"MasaMalilibForgeFix.java", "MasaMalilibForgeFixTweaker.java"}, files);
    }

    /** A class shaped like masa's: one method with @Inject(at = @At(value = "INVOKE", target = ..., shift = AFTER)). */
    private static byte[] mixinWithTarget(String target) {
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "fi/dy/masa/malilib/mixin/render/EntityRendererMixin", null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(Opcodes.ACC_PRIVATE, "onRenderScreenPost", "(FJLorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V", null, null);
        AnnotationVisitor inject = method.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", false);
        AnnotationVisitor methods = inject.visitArray("method");
        methods.visit(null, "updateCameraAndRender(FJ)V");
        methods.visitEnd();
        AnnotationVisitor at = inject.visitAnnotation("at", "Lorg/spongepowered/asm/mixin/injection/At;");
        at.visit("value", "INVOKE");
        at.visit("target", target);
        at.visitEnum("shift", "Lorg/spongepowered/asm/mixin/injection/At$Shift;", "AFTER");
        at.visitEnd();
        inject.visitEnd();
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 5);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static String text(byte[] bytes) {
        return new String(bytes, StandardCharsets.ISO_8859_1);
    }
}
