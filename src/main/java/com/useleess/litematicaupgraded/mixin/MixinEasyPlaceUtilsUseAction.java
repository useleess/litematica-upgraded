package com.useleess.litematicaupgraded.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.useleess.litematicaupgraded.litematica.util.EasyPlaceUtils;

/**
 * Easy Place asks whether the block it clicks does something on a right click (a chest, a crafting table...), to sneak
 * for the click then. The converted code looks the method up with malilib's BlockPos, which no block has, so the answer
 * was always "no" and Easy Place opened the chest instead of placing against it. The lookup is redone with the game's BlockPos.
 */
@Mixin(value = EasyPlaceUtils.class, remap = false)
public abstract class MixinEasyPlaceUtilsUseAction
{
    @Inject(method = "hasUseAction", at = @At("HEAD"), cancellable = true)
    private static void litematicaUpgradedHasUseAction(Block block, CallbackInfoReturnable<Boolean> cir)
    {
        Class<?>[] types = {World.class, BlockPos.class, IBlockState.class, EntityPlayer.class, EnumHand.class, EnumFacing.class,
                            float.class, float.class, float.class};
        // the development name, then the SRG one
        for (String name : new String[] {"onBlockActivated", "func_180639_a"})
        {
            try
            {
                cir.setReturnValue(!block.getClass().getMethod(name, types).equals(Block.class.getMethod(name, types)));
                return;
            }
            catch (NoSuchMethodException ignored)
            {
            }
        }
    }
}
