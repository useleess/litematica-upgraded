package com.useleess.litematicaupgraded.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.EnumActionResult;
import net.minecraft.world.World;

import com.useleess.litematicaupgraded.litematica.util.EasyPlaceUtils;
import com.useleess.litematicaupgraded.malilib.util.position.BlockPos;
import com.useleess.litematicaupgraded.printer.FakeRotation;

/**
 * A placement cannot choose whether a trapdoor or fence gate is open, a lever on, or a repeater's delay: Easy Place
 * clicks the block it just placed until it matches the schematic, and a click aimed at a block that is built but in the
 * wrong one of those states fixes it (instead of the "can't place" warning).
 */
@Mixin(value = EasyPlaceUtils.class, remap = false)
public abstract class MixinEasyPlaceUtilsStateClicks
{
    @Unique
    private static boolean litematicaUpgradedStateFixed;

    @Shadow
    private static void cacheEasyPlacePosition(BlockPos pos) {}

    @Shadow
    private static boolean canPlaceBlock(BlockPos targetPos, World worldClient, IBlockState stateSchematic, IBlockState stateClient) { return false; }

    @Redirect(method = "handleEasyPlace", at = @At(value = "INVOKE",
              target = "Lcom/useleess/litematicaupgraded/litematica/util/EasyPlaceUtils;cacheEasyPlacePosition(Lcom/useleess/litematicaupgraded/malilib/util/position/BlockPos;)V"))
    private static void litematicaUpgradedPlacedThenClick(BlockPos pos)
    {
        cacheEasyPlacePosition(pos);
        litematicaUpgradedFixState(pos);
    }

    @Redirect(method = "handleEasyPlace", at = @At(value = "INVOKE",
              target = "Lcom/useleess/litematicaupgraded/litematica/util/EasyPlaceUtils;canPlaceBlock(Lcom/useleess/litematicaupgraded/malilib/util/position/BlockPos;Lnet/minecraft/world/World;Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/block/state/IBlockState;)Z"))
    private static boolean litematicaUpgradedCanPlaceOrClick(BlockPos pos, World world, IBlockState stateSchematic, IBlockState stateClient)
    {
        if (canPlaceBlock(pos, world, stateSchematic, stateClient)) return true;
        if (litematicaUpgradedFixState(pos))
        {
            cacheEasyPlacePosition(pos);
            litematicaUpgradedStateFixed = true;
        }
        return false;
    }

    @Inject(method = "handleEasyPlace", at = @At("RETURN"), cancellable = true)
    private static void litematicaUpgradedNoWarningAfterStateClick(CallbackInfoReturnable<EnumActionResult> cir)
    {
        if (!litematicaUpgradedStateFixed) return;
        litematicaUpgradedStateFixed = false;
        cir.setReturnValue(EnumActionResult.SUCCESS);
    }

    @Unique
    private static boolean litematicaUpgradedFixState(BlockPos pos)
    {
        Minecraft mc = Minecraft.getMinecraft();
        return FakeRotation.fixState(mc.player, mc.world, new net.minecraft.util.math.BlockPos(pos.getX(), pos.getY(), pos.getZ()));
    }
}
