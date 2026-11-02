package com.useleess.litematicaupgraded.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.useleess.litematicaupgraded.entities.CopiedEntities;
import com.useleess.litematicaupgraded.litematica.Litematica;
import com.useleess.litematicaupgraded.litematica.data.SchematicHolder;
import com.useleess.litematicaupgraded.litematica.schematic.LoadedSchematic;

/** A schematic loaded into memory is checked once for the old entity copy bug (see {@link CopiedEntities}). */
@Mixin(value = SchematicHolder.class, remap = false)
public abstract class MixinSchematicHolderCopiedEntities
{
    @Inject(method = "addSchematic", at = @At(value = "INVOKE", target = "Ljava/util/List;add(Ljava/lang/Object;)Z", shift = At.Shift.AFTER))
    private void copied$check(LoadedSchematic schematic, boolean allowDuplicates, CallbackInfo ci)
    {
        try
        {
            CopiedEntities.warnIfCopied(schematic);
        }
        catch (RuntimeException e)
        {
            // only a warning: a schematic it cannot read still loads as before
            Litematica.LOGGER.warn("Could not check the schematic for copied entities", e);
        }
    }
}
