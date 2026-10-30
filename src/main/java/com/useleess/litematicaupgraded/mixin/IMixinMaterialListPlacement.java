package com.useleess.litematicaupgraded.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.useleess.litematicaupgraded.litematica.materials.MaterialListPlacement;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;

/** The placement a placement's material list counts (for the material list's Replace). */
@Mixin(MaterialListPlacement.class)
public interface IMixinMaterialListPlacement
{
    @Accessor(value = "placement", remap = false)
    SchematicPlacement litematicaUpgradedPlacement();
}
