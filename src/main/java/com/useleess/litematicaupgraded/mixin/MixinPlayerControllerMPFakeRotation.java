package com.useleess.litematicaupgraded.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.useleess.litematicaupgraded.litematica.util.EasyPlaceUtils;
import com.useleess.litematicaupgraded.printer.FakeRotation;

/** Litematica's Easy Place click gets the rotation the schematic block needs reported to the server (Easy Place without a server mod). */
@Mixin(net.minecraft.client.multiplayer.PlayerControllerMP.class)
public abstract class MixinPlayerControllerMPFakeRotation
{
    @Inject(method = "processRightClickBlock", at = @At("HEAD"), cancellable = true)
    private void litematicaUpgradedFakeRotation(
            net.minecraft.client.entity.EntityPlayerSP player,
            net.minecraft.client.multiplayer.WorldClient world,
            net.minecraft.util.math.BlockPos pos,
            net.minecraft.util.EnumFacing direction,
            net.minecraft.util.math.Vec3d vec,
            net.minecraft.util.EnumHand hand,
            CallbackInfoReturnable<net.minecraft.util.EnumActionResult> cir)
    {
        // only the click Easy Place makes (it sets the flag while it does)
        if (EasyPlaceUtils.isHandling())
        {
            net.minecraft.util.EnumActionResult result = FakeRotation.click(player, world, pos, direction, hand);

            if (result != null)
            {
                cir.setReturnValue(result);
            }
        }
    }
}
