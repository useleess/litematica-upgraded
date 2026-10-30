// Printer range, extended with the server part, after Schematica Upgraded's PrinterRange (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.printer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import com.useleess.litematicaupgraded.server.ServerFeatures;
import com.useleess.litematicaupgraded.server.net.MessageRangedClick;
import com.useleess.litematicaupgraded.server.net.MessageRangedDig;
import com.useleess.litematicaupgraded.server.net.Net;

/**
 * The printer's work range. Without the server part it is capped at VANILLA_CAP, which a vanilla server always accepts
 * (reach + 3 from the feet); with the server part at the server's range. Printer clicks and mining beyond the vanilla
 * distance go to the server part as messages; the player's own reach keeps the vanilla distance.
 */
public final class PrinterRange {
    /** Eye distance a vanilla server accepts for clicks whatever the player's height and reach attribute. */
    static final double VANILLA_CAP = 6;
    /** Eye distance a vanilla server accepts for dig packets (36 squared from the feet + 1.5), with a margin. */
    static final double VANILLA_DIG = 5.5;
    /** A click this much inside the vanilla limit (client position) is not repeated as a message. */
    private static final double MARGIN = 1;

    /** The messages sent so far instead of a vanilla click / dig packet (for tests). */
    public static volatile int rangedClicksSent, rangedDigsSent;

    private PrinterRange() {}

    /** The farthest range allowed now: the server's extended range with the server part, else VANILLA_CAP. */
    public static double cap() {
        return Math.max(VANILLA_CAP, ServerFeatures.range);
    }

    /** The printer's work range: the setting, limited by what the server accepts. */
    public static int printer() {
        return (int) Math.min(PrinterConfigs.RANGE.getIntegerValue(), cap());
    }

    /** The range in which the printer may mine: the printer range, capped at what a vanilla server accepts for dig packets without the server part. */
    public static double mining(double range) {
        return ServerFeatures.range > 0 ? range : Math.min(range, VANILLA_DIG);
    }

    /**
     * Sends a dig action. Beyond the vanilla dig distance with the server part it goes as a message instead of the
     * vanilla packet (which the server would ignore); exactly one of the two is sent.
     */
    public static void dig(EntityPlayerSP player, CPacketPlayerDigging.Action action, BlockPos pos, EnumFacing side) {
        if (ServerFeatures.range > 0 && beyondVanillaDig(player, pos)) {
            int kind = action == CPacketPlayerDigging.Action.START_DESTROY_BLOCK ? MessageRangedDig.START
                : action == CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK ? MessageRangedDig.STOP : MessageRangedDig.ABORT;
            Net.CHANNEL.sendToServer(new MessageRangedDig(kind, pos, side));
            rangedDigsSent++;
        } else {
            player.connection.sendPacket(new CPacketPlayerDigging(action, pos, side));
        }
    }

    /** NetHandlerPlayServer.processPlayerDigging's check ((reach + 1) from the feet + 1.5) may refuse the dig. */
    static boolean beyondVanillaDig(EntityPlayerSP player, BlockPos pos) {
        double reach = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue() + 1 - MARGIN / 2;
        double dx = player.posX - (pos.getX() + 0.5), dy = player.posY - (pos.getY() + 0.5) + 1.5, dz = player.posZ - (pos.getZ() + 0.5);
        return dx * dx + dy * dy + dz * dz >= reach * reach;
    }

    /**
     * Uses the held item on a block face like processRightClickBlock (the client predicts the result). When the
     * vanilla server may ignore the click for its distance, the click goes to the server part as a message too, sent
     * first; the server performs the message only when it ignores the vanilla packet, so the click acts once.
     */
    public static EnumActionResult click(EntityPlayerSP player, BlockPos pos, EnumFacing side, Vec3d hit, EnumHand hand) {
        Minecraft mc = Minecraft.getMinecraft();
        if (ServerFeatures.range > 0 && beyondVanilla(player, pos)) {
            player.connection.sendPacket(new CPacketHeldItemChange(player.inventory.currentItem));
            Net.CHANNEL.sendToServer(new MessageRangedClick(pos, side,
                (float) (hit.x - pos.getX()), (float) (hit.y - pos.getY()), (float) (hit.z - pos.getZ())));
            rangedClicksSent++;
        }
        return mc.playerController.processRightClickBlock(player, mc.world, pos, side, hit, hand);
    }

    /** Whether the server's vanilla check may refuse the click (the server's position may differ a little). */
    static boolean beyondVanilla(EntityPlayerSP player, BlockPos pos) {
        double reach = player.getEntityAttribute(EntityPlayer.REACH_DISTANCE).getAttributeValue() + 3 - MARGIN;
        return player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) >= reach * reach;
    }
}
