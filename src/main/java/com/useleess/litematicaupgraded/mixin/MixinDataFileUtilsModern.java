package com.useleess.litematicaupgraded.mixin;

import java.nio.file.Path;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.useleess.litematicaupgraded.convert.ModernFiles;
import com.useleess.litematicaupgraded.malilib.util.data.tag.CompoundData;
import com.useleess.litematicaupgraded.malilib.util.data.tag.util.DataFileUtils;

/**
 * Every schematic file is read here (the browser's info, loading, reloading): data of a Minecraft version newer than
 * 1.12 leaves it already converted, so Litematica's own readers see a 1.12 file. Other files pass unchanged.
 */
@Mixin(value = DataFileUtils.class, remap = false)
public abstract class MixinDataFileUtilsModern
{
    @Inject(method = "readCompoundDataFromNbtFile", at = @At("RETURN"), cancellable = true)
    private static void litematicaUpgradedConvert(Path file, CallbackInfoReturnable<CompoundData> cir)
    {
        cir.setReturnValue(ModernFiles.toLegacy(cir.getReturnValue()));
    }
}
