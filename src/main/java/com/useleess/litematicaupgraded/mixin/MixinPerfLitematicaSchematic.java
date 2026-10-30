package com.useleess.litematicaupgraded.mixin;

import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.useleess.litematicaupgraded.litematica.schematic.LitematicaSchematic;
import com.useleess.litematicaupgraded.litematica.schematic.container.ArrayBlockContainer;
import com.useleess.litematicaupgraded.malilib.util.position.Vec3i;
import com.useleess.litematicaupgraded.perf.PerfConfigs;
import com.useleess.litematicaupgraded.perf.SparseIntArray;

/**
 * A region whose packed block array is mostly zero (air) is kept in 16 x 16 x 16 sections that exist only where there are
 * blocks, instead of one array for the whole box. The container on top is the same, only its storage differs.
 */
@Mixin(value = LitematicaSchematic.class, remap = false)
public abstract class MixinPerfLitematicaSchematic
{
    @Inject(method = "createContainerFromData", at = @At("HEAD"), cancellable = true)
    private static void perf$sparseContainer(Vec3i size, int paletteSize, @Nullable long[] blockStates, CallbackInfoReturnable<ArrayBlockContainer> cir)
    {
        if (PerfConfigs.SPARSE_STORAGE.getBooleanValue() == false || SparseIntArray.worthIt(blockStates) == false)
        {
            return;
        }

        int bits = ArrayBlockContainer.getRequiredBitWidth(paletteSize);
        int x = Math.abs(size.getX()), y = Math.abs(size.getY()), z = Math.abs(size.getZ());
        SparseIntArray storage = SparseIntArray.fromPacked(bits, x, y, z, blockStates);

        if (storage != null)
        {
            cir.setReturnValue(new ArrayBlockContainer(new Vec3i(x, y, z), storage));
        }
    }
}
