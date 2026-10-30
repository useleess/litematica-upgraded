// The client side of accurate placement, air placement and shulker restock, after Schematica Upgraded (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.printer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;

import com.useleess.litematicaupgraded.server.AccuratePlacement;
import com.useleess.litematicaupgraded.server.AirPlacement;
import com.useleess.litematicaupgraded.server.ServerFeatures;
import com.useleess.litematicaupgraded.server.ShulkerRestock;
import com.useleess.litematicaupgraded.server.StateStrings;
import com.useleess.litematicaupgraded.server.net.MessageAirPlace;
import com.useleess.litematicaupgraded.server.net.MessagePlacementIntent;
import com.useleess.litematicaupgraded.server.net.MessageShulkerTake;
import com.useleess.litematicaupgraded.server.net.Net;

/** What the printer asks of the server part. Every call does nothing (or says no) while the server does not report the feature on. */
public final class ServerAssist {
    /** A request waits this long for the server's update before the same one may be sent again. */
    static final long RETRY_MILLIS = 1000;
    private static final Map<BlockPos, Long> airPending = new HashMap<>();
    private static final Map<String, Long> shulkerPending = new HashMap<>();

    /** The requests sent so far (for tests). */
    public static volatile int intentsSent, airSent, shulkerSent;

    private ServerAssist() {}

    // ---- accurate placement ----

    /** Whether the server will set this block's orientation. */
    public static boolean accurate(Block block) {
        return ServerFeatures.accuratePlacement && AccuratePlacement.supports(block);
    }

    /** Tells the server the state the next placement at pos should get; sent just before the click. */
    public static void announce(BlockPos pos, IBlockState state) {
        Net.CHANNEL.sendToServer(new MessagePlacementIntent(pos.getX(), pos.getY(), pos.getZ(), StateStrings.toString(state)));
        intentsSent++;
    }

    // ---- air placement ----

    public static boolean air(IBlockState state) {
        return ServerFeatures.airPlacement && AirPlacement.supports(state);
    }

    /** Whether a request for pos is still waiting for the server. */
    static boolean airWaiting(BlockPos pos) {
        expire(airPending.values());
        return airPending.containsKey(pos);
    }

    /** Sends the held slot (the server must know it first) and the request; the held item must be the wanted block. */
    static void airPlace(EntityPlayerSP player, BlockPos pos, IBlockState state) {
        player.connection.sendPacket(new CPacketHeldItemChange(player.inventory.currentItem));
        Net.CHANNEL.sendToServer(new MessageAirPlace(pos, StateStrings.toString(state)));
        airPending.put(pos.toImmutable(), System.currentTimeMillis());
        airSent++;
    }

    // ---- shulker restock ----

    /**
     * When the printer lacks an item that a shulker box in the inventory holds, the server is asked to move a stack of it
     * out of the box; the item arrives with the server's inventory update and is used on a later pass. True when a request
     * is sent or still pending.
     */
    public static boolean restock(EntityPlayerSP player, ItemStack wanted) {
        if (!ServerFeatures.shulkerRestock || player == null || wanted.isEmpty() || player.openContainer != player.inventoryContainer) return false;
        String key = Item.getIdFromItem(wanted.getItem()) + ":" + wanted.getMetadata();
        expire(shulkerPending.values());
        if (shulkerPending.containsKey(key)) return true;
        if (!hasRoom(player, wanted)) return false;
        int[] found = find(player.inventory.mainInventory, wanted);
        if (found == null) return false;
        Net.CHANNEL.sendToServer(new MessageShulkerTake(found[0], found[1], Item.getIdFromItem(wanted.getItem()), wanted.getMetadata(), wanted.getMaxStackSize()));
        shulkerPending.put(key, System.currentTimeMillis());
        shulkerSent++;
        return true;
    }

    /** A free slot, or a stack of the item with room left. */
    static boolean hasRoom(EntityPlayerSP player, ItemStack wanted) {
        if (player.inventory.getFirstEmptyStack() >= 0) return true;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack.isItemEqual(wanted) && ItemStack.areItemStackTagsEqual(stack, wanted) && stack.getCount() < stack.getMaxStackSize()) return true;
        }
        return false;
    }

    /** The inventory slot of a box holding the item and the box slot with the most of it, or null. */
    static int[] find(NonNullList<ItemStack> inventory, ItemStack wanted) {
        int[] best = null;
        int most = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack box = inventory.get(slot);
            if (!ShulkerRestock.isBox(box)) continue;
            NonNullList<ItemStack> contents = ShulkerRestock.contents(box);
            for (int inner = 0; inner < contents.size(); inner++) {
                ItemStack stack = contents.get(inner);
                if (!stack.isEmpty() && stack.isItemEqual(wanted) && stack.getCount() > most) {
                    most = stack.getCount();
                    best = new int[] {slot, inner};
                }
            }
        }
        return best;
    }

    private static void expire(Iterable<Long> times) {
        long now = System.currentTimeMillis();
        for (Iterator<Long> it = times.iterator(); it.hasNext();) if (now - it.next() > RETRY_MILLIS) it.remove();
    }
}
