package com.useleess.litematicaupgraded.printer;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.ContainerDispenser;
import net.minecraft.inventory.ContainerHopper;
import net.minecraft.inventory.ContainerShulkerBox;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.useleess.litematicaupgraded.litematica.Litematica;
import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.materials.MaterialListEntry;
import com.useleess.litematicaupgraded.litematica.materials.MaterialListUtils;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;
import com.useleess.litematicaupgraded.litematica.selection.SelectionBox;
import com.useleess.litematicaupgraded.litematica.world.SchematicWorldHandler;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageDispatcher;
import com.useleess.litematicaupgraded.malilib.util.data.EnabledCondition;
import com.useleess.litematicaupgraded.malilib.util.position.Direction;
import com.useleess.litematicaupgraded.malilib.util.position.IntBoundingBox;
import com.useleess.litematicaupgraded.malilib.util.position.LayerRange;
import com.useleess.litematicaupgraded.malilib.util.world.BlockState;

/**
 * Grab From Containers: while the switch is on, a chest, shulker box, hopper, dropper or dispenser the player opens
 * gives up what the selected layers of the loaded placements still need. Only normal shift-clicks, so it works on any
 * server. The count is spread over ticks (a whole big placement in one frame would stutter); only blocks whose chunks
 * are loaded can be compared, so that is what counts. Whole stacks are taken, the most needed item first.
 */
public final class ContainerGrab {
    public static final ContainerGrab INSTANCE = new ContainerGrab();

    /** Positions compared per tick. */
    static final int SCAN_PER_TICK = 65536;
    /** Ticks with nothing to take before the job ends: the contents may arrive after the screen. */
    static final int IDLE_TICKS = 20;

    /** How the last job ended (for tests). */
    public static String lastEnd = "none yet";

    private Container container;
    private Job job;

    private ContainerGrab() {}

    /** One item still wanted, and how many. */
    static final class Need {
        final ItemStack stack;
        long count;
        Need(ItemStack stack, long count) { this.stack = stack; this.count = count; }
    }

    /** What the list asks for beyond what the player carries, the most needed first. */
    static List<Need> needs(List<MaterialListEntry> entries) {
        List<Need> needs = new ArrayList<>();
        for (MaterialListEntry entry : entries) {
            long count = entry.getMissingCount() - entry.getAvailableCount();
            if (count > 0) needs.add(new Need(entry.getStack(), count));
        }
        needs.sort(Comparator.comparingLong((Need need) -> need.count).reversed());
        return needs;
    }

    /** The first need still open that the slots can serve, or null. */
    static Need next(List<Need> needs, List<ItemStack> slots) {
        for (Need need : needs) {
            if (need.count > 0 && slotOf(need, slots) >= 0) return need;
        }
        return null;
    }

    /** The index of the first slot holding the item, or -1. */
    static int slotOf(Need need, List<ItemStack> slots) {
        for (int i = 0; i < slots.size(); i++) if (ContainerFiller.same(slots.get(i), need.stack)) return i;
        return -1;
    }

    /** The screens items are taken from; a furnace or crafting table holds work in progress, not storage. */
    static boolean storage(Container container) {
        return container instanceof ContainerChest || container instanceof ContainerShulkerBox
            || container instanceof ContainerHopper || container instanceof ContainerDispenser;
    }

    /** The lower and upper bound of a box side, cut to the layers when the layers run along this axis. */
    static int[] cut(int min, int max, Direction.Axis side, LayerRange layers) {
        if (side != layers.getAxis()) return new int[] {min, max};
        return new int[] {Math.max(min, layers.getMinLayerBoundary()), Math.min(max, layers.getMaxLayerBoundary())};
    }

    /** The missing blocks of the selected layers, a slice per tick. */
    private static final class Scan {
        final Object2LongOpenHashMap<BlockState> missing = new Object2LongOpenHashMap<>();
        final Deque<Iterable<BlockPos.MutableBlockPos>> boxes = new ArrayDeque<>();
        Iterator<BlockPos.MutableBlockPos> positions = Collections.emptyIterator();

        Scan(Minecraft mc, EntityPlayerSP player) {
            LayerRange layers = DataManager.getRenderLayerRange();
            int view = (mc.gameSettings.renderDistanceChunks + 1) * 16;
            int px = (int) player.posX, pz = (int) player.posZ;
            for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllSchematicPlacements()) {
                if (!placement.isEnabled()) continue;
                for (SelectionBox box : placement.getSubRegionBoxes(EnabledCondition.ENABLED).values()) {
                    IntBoundingBox bb = box.asIntBoundingBox();
                    int[] x = cut(Math.max(bb.minX, px - view), Math.min(bb.maxX, px + view), Direction.Axis.X, layers);
                    int[] y = cut(bb.minY, bb.maxY, Direction.Axis.Y, layers);
                    int[] z = cut(Math.max(bb.minZ, pz - view), Math.min(bb.maxZ, pz + view), Direction.Axis.Z, layers);
                    if (x[0] > x[1] || y[0] > y[1] || z[0] > z[1]) continue;
                    this.boxes.add(BlockPos.getAllInBoxMutable(new BlockPos(x[0], y[0], z[0]), new BlockPos(x[1], y[1], z[1])));
                }
            }
        }

        /** Compares the next slice; true when every box is done. */
        boolean run(World world, World schematic) {
            for (int done = 0; done < SCAN_PER_TICK; done++) {
                while (!this.positions.hasNext()) {
                    if (this.boxes.isEmpty()) return true;
                    this.positions = this.boxes.poll().iterator();
                }
                BlockPos pos = this.positions.next();
                Block wanted = schematic.getBlockState(pos).getBlock();
                if (wanted != Blocks.AIR && world.getBlockState(pos).getBlock() != wanted) {
                    this.missing.addTo(BlockState.of(schematic.getBlockState(pos)), 1);
                }
            }
            return false;
        }
    }

    private static final class Job {
        final Scan scan;
        List<Need> needs;
        int taken, idle, wait;
        Job(Scan scan) { this.scan = scan; }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayerSP player = mc.player;
        if (!PrinterConfigs.CONTAINER_GRAB.getBooleanValue() || player == null || mc.world == null
            || !(mc.currentScreen instanceof GuiContainer) || player.openContainer == player.inventoryContainer) {
            forget();
            return;
        }
        try {
            if (player.openContainer != this.container) {
                forget();
                this.container = player.openContainer;
                if (storage(this.container) && !Printer.INSTANCE.fillingContainer()) this.job = new Job(new Scan(mc, player));
            }
            if (this.job != null) step(mc, player);
        } catch (RuntimeException e) {
            Litematica.LOGGER.error("Grab From Containers failed", e);
            this.job = null;
            PrinterConfigs.CONTAINER_GRAB.setBooleanValue(false);
            MessageDispatcher.error().translate("litematica_upgraded.message.container_grab.failed");
        }
    }

    private void step(Minecraft mc, EntityPlayerSP player) {
        Job job = this.job;
        if (job.needs == null) {
            World schematic = SchematicWorldHandler.getSchematicWorld();
            if (schematic == null) {
                end("no schematic world");
            } else if (job.scan.run(mc.world, schematic)) {
                job.needs = needs(MaterialListUtils.getMaterialList(job.scan.missing, job.scan.missing, new Object2LongOpenHashMap<>()));
            }
            return;
        }
        if (job.wait > 0) {
            job.wait--;
            return;
        }
        List<Slot> slots = new ArrayList<>();
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < this.container.inventorySlots.size(); i++) {
            Slot slot = this.container.getSlot(i);
            if (slot.inventory == player.inventory) continue;
            slots.add(slot);
            stacks.add(slot.getStack());
        }
        Need need = next(job.needs, stacks);
        if (need == null) {
            if (++job.idle > IDLE_TICKS) end("nothing more to take");
            return;
        }
        job.idle = 0;
        Slot slot = slots.get(slotOf(need, stacks));
        int before = slot.getStack().getCount();
        mc.playerController.windowClick(this.container.windowId, slot.slotNumber, 0, ClickType.QUICK_MOVE, player);
        int moved = before - slot.getStack().getCount();
        need.count -= moved;
        job.taken += moved;
        job.wait = PrinterConfigs.CONTAINER_GRAB_CLICK_TICKS.getIntegerValue() - 1;
        if (moved == 0) {
            MessageDispatcher.warning().translate("litematica_upgraded.message.container_grab.full", job.taken);
            job.taken = 0;
            end("inventory full");
        }
    }

    /** Ends the job and tells the player what it took. */
    private void end(String why) {
        Job job = this.job;
        this.job = null;
        lastEnd = why;
        if (job.taken > 0) MessageDispatcher.success().translate("litematica_upgraded.message.container_grab.taken", job.taken);
    }

    private void forget() {
        if (this.job != null) end("the screen was closed");
        this.container = null;
    }
}
