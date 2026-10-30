// Filling placed containers (item filters in hoppers, droppers and dispensers; chests, furnaces, brewing stands, shulker boxes), after the behavior of sakura-ryoko's litematica-printer2, by HackerRouter, 2026 (Schematica Upgraded, MIT).
package com.useleess.litematicaupgraded.printer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBrewingStand;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.BlockHopper;
import net.minecraft.block.BlockShulkerBox;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A placed container (hopper, dropper, dispenser, chest, trapped chest, furnace, brewing stand, shulker box) whose
 * schematic block holds items is opened once it is built and the player carries all of its items; a double chest is
 * filled through one of its halves once both stand, with the slots of both (the west / north half first, as the game
 * joins them). A furnace's output slot is skipped (the game takes nothing put there); the items go into the schematic's slots, one slot a tick, and the
 * screen is closed. A container is filled once per refresh: the client cannot see its contents without opening it.
 * Slots that hold other items are left alone.
 */
final class ContainerFiller {
    /** Ticks to wait for the screen to open, and for its contents to arrive once open. */
    static final int OPEN_TIMEOUT = 20, SETTLE = 2;
    /**
     * Passes over the slots at most, and the ticks between two passes: where the game's own guess of a click differs
     * from the server (a brewing stand's bottle slots), the server ignores the next clicks until it has sent the real
     * contents, so a pass is followed by a look at what the server says and another pass for the slots still missing.
     */
    static final int PASSES = 5, RESYNC = 6;
    /** The vanilla reach for opening a block, from the eyes. */
    static final double REACH = 4.5;

    /** The containers filled so far (for tests and the debug screen); the newest 256. */
    static final List<BlockPos> FILLED = new ArrayList<>();
    /** How the last fill ended (for tests): "filled", or why it stopped early. */
    static String lastEnd = "none yet";

    private final Set<BlockPos> done = new HashSet<>();
    private World world;
    private Job job;

    private static final class Job {
        final BlockPos pos;
        final ItemStack[] wanted;
        /** The other half of a double chest, or null. */
        BlockPos partner;
        /** The slots clicked into, with the count each time (for lastEnd). */
        final StringBuilder clicked = new StringBuilder();
        int waited, settled, slot, pass;
        int windowId = -1;
        Job(BlockPos pos, ItemStack[] wanted) { this.pos = pos; this.wanted = wanted; }
    }


    void clear() {
        this.done.clear();
        this.job = null;
    }

    boolean busy() { return this.job != null; }

    /** The containers the printer fills: hoppers, droppers, dispensers (item filters), chests and trapped chests, furnaces, brewing stands, shulker boxes. */
    static boolean fillable(Block block) {
        return block instanceof BlockHopper || block instanceof BlockDispenser || block instanceof BlockChest
            || block instanceof BlockFurnace || block instanceof BlockBrewingStand || block instanceof BlockShulkerBox;
    }

    /** Whether a player can put an item into this slot of the block's screen (a furnace's output slot takes nothing). */
    static boolean playerFillable(Block block, int slot) {
        return !(block instanceof BlockFurnace && slot == 2);
    }

    /** The slots of a double chest as the game joins them: the half to the west or north first. */
    static ItemStack[] joined(ItemStack[] own, ItemStack[] partner, EnumFacing towardsPartner) {
        boolean partnerFirst = towardsPartner == EnumFacing.WEST || towardsPartner == EnumFacing.NORTH;
        ItemStack[] first = partnerFirst ? partner : own, second = partnerFirst ? own : partner;
        ItemStack[] all = new ItemStack[first.length + second.length];
        System.arraycopy(first, 0, all, 0, first.length);
        System.arraycopy(second, 0, all, first.length, second.length);
        return all;
    }

    /** The schematic's items per slot for a built container, or null when it holds none, was filled already or waits for its other half. */
    ItemStack[] wanted(World world, World schematic, BlockPos pos) {
        if (world != this.world) {
            this.done.clear();
            this.world = world;
        }
        Block block = schematic.getBlockState(pos).getBlock();
        if (this.done.contains(pos) || !fillable(block)) return null;
        ItemStack[] wanted = slots(schematic, pos, block);
        if (wanted == null) return null;
        EnumFacing side = partnerSide(schematic, pos, block);
        if (side != null) {
            // a double chest: filled once both halves stand (a single half's screen has the wrong slots)
            BlockPos other = pos.offset(side);
            ItemStack[] partnerSlots = slots(schematic, other, block);
            if (partnerSlots == null || world.getBlockState(other).getBlock() != block || this.done.contains(other)) return null;
            wanted = joined(wanted, partnerSlots, side);
        }
        for (ItemStack stack : wanted) if (!stack.isEmpty()) return wanted;
        return null;
    }

    /** The side the other half of a double chest is on, or null for any other block. */
    private static EnumFacing partnerSide(World world, BlockPos pos, Block block) {
        if (!(block instanceof BlockChest)) return null;
        for (EnumFacing side : EnumFacing.Plane.HORIZONTAL) if (world.getBlockState(pos.offset(side)).getBlock() == block) return side;
        return null;
    }

    /** The schematic container's items per slot (the ones a player can put in), or null when it has no inventory. */
    private static ItemStack[] slots(World schematic, BlockPos pos, Block block) {
        TileEntity tile = schematic.getTileEntity(pos);
        if (!(tile instanceof IInventory)) return null;
        IInventory inventory = (IInventory) tile;
        ItemStack[] slots = new ItemStack[inventory.getSizeInventory()];
        for (int i = 0; i < slots.length; i++) slots[i] = playerFillable(block, i) ? inventory.getStackInSlot(i) : ItemStack.EMPTY;
        return slots;
    }

    /** Whether the player carries every item of the filter. */
    static boolean carries(EntityPlayerSP player, ItemStack[] wanted) {
        List<ItemStack> needed = new ArrayList<>();
        for (ItemStack stack : wanted) {
            if (stack.isEmpty()) continue;
            ItemStack total = null;
            for (ItemStack other : needed) if (same(other, stack)) total = other;
            if (total == null) needed.add(stack.copy());
            else total.grow(stack.getCount());
        }
        for (ItemStack stack : needed) {
            int have = 0;
            for (ItemStack own : player.inventory.mainInventory) if (same(own, stack)) have += own.getCount();
            if (have < stack.getCount()) return false;
        }
        return true;
    }

    /** Item and damage; names and other tags are not required (a renamed filter item is as good as a plain one). */
    static boolean same(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && a.isItemEqual(b);
    }

    /** Opens the container with a click (not sneaking, so the block's screen opens instead of placing the held item). */
    boolean start(Minecraft minecraft, EntityPlayerSP player, BlockPos pos, ItemStack[] wanted) {
        if (minecraft.currentScreen != null || !carries(player, wanted)) return false;
        Vec3d hit = new Vec3d(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5);
        if (minecraft.playerController.processRightClickBlock(player, minecraft.world, pos, EnumFacing.UP, hit, EnumHand.MAIN_HAND)
            != EnumActionResult.SUCCESS) {
            this.done.add(pos);
            return false;
        }
        player.swingArm(EnumHand.MAIN_HAND);
        this.job = new Job(pos, wanted);
        EnumFacing side = partnerSide(minecraft.world, pos, minecraft.world.getBlockState(pos).getBlock());
        if (side != null) this.job.partner = pos.offset(side);
        return true;
    }

    /** One step of the open job: waits for the screen, fills one slot, closes when done. */
    void tick(Minecraft minecraft, EntityPlayerSP player) {
        Job job = this.job;
        Container container = player.openContainer;
        boolean open = minecraft.currentScreen instanceof GuiContainer && container != null && container != player.inventoryContainer;
        if (job.windowId < 0) {
            if (!open) {
                if (++job.waited > OPEN_TIMEOUT) finish(job, null, "the screen did not open");
                return;
            }
            job.windowId = container.windowId;
        }
        if (!open || container.windowId != job.windowId) {
            // closed by the player (or replaced by another screen): not tried again until a refresh
            finish(job, null, "the screen was closed");
            return;
        }
        if (++job.settled <= SETTLE) return;
        List<Slot> own = new ArrayList<>(), block = new ArrayList<>();
        for (Slot slot : container.inventorySlots) (slot.inventory == player.inventory ? own : block).add(slot);
        if (block.size() != job.wanted.length) {
            finish(job, player, "the screen has " + block.size() + " slots, the schematic " + job.wanted.length);
            return;
        }
        if (!player.inventory.getItemStack().isEmpty()) {
            // an item left on the cursor by a click the server answered differently: back into the inventory first
            putBack(minecraft, player, container, own);
            return;
        }
        while (job.slot < job.wanted.length) {
            int index = job.slot++;
            if (fill(minecraft, player, container, slotFor(block, index), own, job.wanted[index])) {
                job.clicked.append(' ').append(job.pass).append('/').append(index);
                return;
            }
        }
        if (++job.pass < PASSES && missing(block, job.wanted)) {
            job.slot = 0;
            job.settled = SETTLE - RESYNC;
            return;
        }
        finish(job, player, missing(block, job.wanted) ? "slots still missing" : "filled");
    }

    /** Whether a wanted slot still lacks items (a slot holding another item is left alone, so it does not count). */
    private static boolean missing(List<Slot> block, ItemStack[] wanted) {
        for (int i = 0; i < wanted.length; i++) {
            ItemStack have = slotFor(block, i).getStack();
            if (!wanted[i].isEmpty() && (have.isEmpty() || same(have, wanted[i]) && have.getCount() < wanted[i].getCount())) return true;
        }
        return false;
    }

    /** Clicks the cursor's item into an empty slot of the player's inventory (or onto the same item). */
    private static void putBack(Minecraft minecraft, EntityPlayerSP player, Container container, List<Slot> own) {
        ItemStack held = player.inventory.getItemStack();
        Slot free = null;
        for (Slot slot : own) {
            ItemStack stack = slot.getStack();
            if (stack.isEmpty() && free == null) free = slot;
            if (same(stack, held) && stack.getCount() < stack.getMaxStackSize()) {
                free = slot;
                break;
            }
        }
        if (free != null) click(minecraft, player, container, free.slotNumber, 0);
    }

    private static Slot slotFor(List<Slot> block, int index) {
        for (Slot slot : block) if (slot.getSlotIndex() == index) return slot;
        return block.get(index);
    }

    /** Moves the wanted count into the slot; true when it clicked (the rest of the slots follow on later ticks). */
    private static boolean fill(Minecraft minecraft, EntityPlayerSP player, Container container, Slot target, List<Slot> own, ItemStack wanted) {
        if (wanted.isEmpty()) return false;
        ItemStack current = target.getStack();
        if (!current.isEmpty() && !same(current, wanted)) return false;
        int need = wanted.getCount() - current.getCount();
        if (need <= 0) return false;
        boolean clicked = false;
        for (Slot source : own) {
            if (need <= 0) break;
            if (!same(source.getStack(), wanted)) continue;
            click(minecraft, player, container, source.slotNumber, 0);
            clicked = true;
            ItemStack held = player.inventory.getItemStack();
            if (held.getCount() <= need) {
                need -= held.getCount();
                click(minecraft, player, container, target.slotNumber, 0);
            } else {
                for (; need > 0; need--) click(minecraft, player, container, target.slotNumber, 1);
            }
            // the rest goes back where it came from
            if (!player.inventory.getItemStack().isEmpty()) click(minecraft, player, container, source.slotNumber, 0);
        }
        return clicked;
    }

    private static void click(Minecraft minecraft, EntityPlayerSP player, Container container, int slot, int button) {
        minecraft.playerController.windowClick(container.windowId, slot, button, ClickType.PICKUP, player);
    }

    private void finish(Job job, EntityPlayerSP player, String end) {
        lastEnd = end + " at " + job.pos + " (slots:" + job.clicked + ")";
        for (BlockPos pos : new BlockPos[] {job.pos, job.partner}) {
            if (pos == null) continue;
            this.done.add(pos);
            FILLED.add(pos);
        }
        while (FILLED.size() > 256) FILLED.remove(0);
        this.job = null;
        if (player != null) player.closeScreen();
    }
}
