package com.useleess.litematicaupgraded.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;

import com.useleess.litematicaupgraded.litematica.util.EasyPlaceUtils;
import com.useleess.litematicaupgraded.litematica.util.PickBlockUtils;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageDispatcher;
import com.useleess.litematicaupgraded.printer.ServerAssist;

/**
 * Easy Place takes a block it needs out of a shulker box in the inventory, like the printer: when the block is not in
 * the inventory, the server (our server part, or a Carpet server with the rule on) is asked to move it out of a carried
 * box. The click then places nothing and shows a note instead of the "can't place" warning; the next click (or holding
 * the button) places the block once it arrived.
 */
@Mixin(value = EasyPlaceUtils.class, remap = false)
public abstract class MixinEasyPlaceUtilsShulkerRestock
{
    @Unique
    private static boolean litematicaUpgradedRestockAsked;

    @Redirect(method = "handleEasyPlace", at = @At(value = "INVOKE",
              target = "Lcom/useleess/litematicaupgraded/litematica/util/PickBlockUtils;doPickBlockForStack(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/util/EnumHand;"))
    private static EnumHand litematicaUpgradedPickOrRestock(ItemStack stack)
    {
        EnumHand hand = PickBlockUtils.doPickBlockForStack(stack);
        if (hand != null) return hand;
        int sent = ServerAssist.shulkerSent;
        litematicaUpgradedRestockAsked = ServerAssist.restock(Minecraft.getMinecraft().player, stack);
        if (ServerAssist.shulkerSent != sent)
        {
            MessageDispatcher.generic(1500).translate("litematica_upgraded.message.easy_place.restock", stack.getDisplayName());
        }
        return null;
    }

    @Inject(method = "handleEasyPlace", at = @At("RETURN"), cancellable = true)
    private static void litematicaUpgradedNoWarningWhileRestocking(CallbackInfoReturnable<EnumActionResult> cir)
    {
        if (!litematicaUpgradedRestockAsked) return;
        litematicaUpgradedRestockAsked = false;
        cir.setReturnValue(EnumActionResult.SUCCESS);
    }
}
