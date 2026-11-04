// Server side of the accurate placement protocol, after Schematica Upgraded (Lunatrius / HackerRouter / Useleess, MIT), itself the counterpart of Litematica's easyPlaceProtocol v3.
package com.useleess.litematicaupgraded.server;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.base.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockQuartz;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.useleess.litematicaupgraded.printer.StateMatch;
import com.useleess.litematicaupgraded.server.net.MessagePlacementIntent;

/**
 * Applies the state a client asked for to the block its placement creates: every property the player could give it by
 * placing and clicking (facing, half, axis, shape, delay, mode, hinge, open, rotation, a lever's switch...). What the
 * world decides stays as vanilla placed it: power (redstone parts start unpowered and the world powers them), growth,
 * neighbours, contents, the block's kind (variant, colour), and the open state of iron doors and trapdoors (only
 * redstone opens them). The block must also drop the same items as before.
 */
public final class AccuratePlacement {
    public static final AccuratePlacement INSTANCE = new AccuratePlacement();
    static final long LIFETIME_NANOS = 2_000_000_000L;
    static final int MAX_PENDING = 64;
    /** Properties kept as vanilla placed them. */
    static final Set<String> WORLD_MADE = new HashSet<>(Arrays.asList(
        "powered", "power", "triggered", "extended", "enabled", "locked", "attached", "disarmed", "occupied", "part", "short",
        "check_decay", "decayable", "snowy", "age", "conditional", "has_bottle_0", "has_bottle_1", "has_bottle_2", "contents",
        "eye", "explode", "nodrop", "has_record", "variant", "type", "color", "wet", "damage", "seamless", "legacy_data"));

    private static final class Intent {
        final String state;
        final long time = System.nanoTime();

        Intent(MessagePlacementIntent message) { state = message.state; }
    }

    /** Intents arrive on the network thread ahead of the placements queued for the server thread, so several can wait. */
    private final Map<UUID, Map<Long, Intent>> intents = new ConcurrentHashMap<>();

    private AccuratePlacement() {}

    /** Whether the block has a property the protocol may set (quartz pillars: their axis variant). */
    public static boolean supports(Block block) {
        if (block == null) return false;
        if (block instanceof BlockQuartz) return true;
        for (IProperty<?> property : block.getBlockState().getProperties()) if (settable(block, property.getName())) return true;
        return false;
    }

    /** Whether a client may choose this property of the block: a lever's switch is the player's, an iron door opens by redstone only. */
    static boolean settable(Block block, String property) {
        if (block instanceof BlockLever && property.equals("powered")) return true;
        if (property.equals("open") && block.getDefaultState().getMaterial() == Material.IRON) return false;
        return !WORLD_MADE.contains(property);
    }

    static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFF) << 38 | ((long) z & 0x3FFFFFF) << 12 | (y & 0xFFF);
    }

    public void intent(EntityPlayerMP player, MessagePlacementIntent message) {
        if (player == null || !ServerConfig.accuratePlacement) return;
        Map<Long, Intent> pending = intents.computeIfAbsent(player.getUniqueID(), id -> new LinkedHashMap<>());
        synchronized (pending) {
            long now = System.nanoTime();
            Iterator<Intent> iterator = pending.values().iterator();
            while (iterator.hasNext() && (now - iterator.next().time > LIFETIME_NANOS || pending.size() >= MAX_PENDING)) iterator.remove();
            long key = key(message.x, message.y, message.z);
            pending.remove(key);
            pending.put(key, new Intent(message));
        }
    }

    public void forget(EntityPlayer player) { intents.remove(player.getUniqueID()); }

    /**
     * States set this tick, kept against the block's own follow-up: Forge calls onBlockAdded of a block without a tile
     * entity only after the place event, and a rail then fits itself to its neighbours again (and a later rail next to it
     * re-shapes it). Each such change notifies the neighbours of the position, where the asked state is put back.
     */
    private final Map<World, Map<Long, IBlockState>> settling = new HashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlace(BlockEvent.PlaceEvent event) {
        if (event.getWorld().isRemote || !(event.getPlayer() instanceof EntityPlayerMP) || event.isCanceled()) return;
        Map<Long, Intent> pending = intents.get(event.getPlayer().getUniqueID());
        if (event instanceof BlockEvent.MultiPlaceEvent) {
            // one item use that changed several blocks (door halves; a chest, rail or tripwire hook that set itself again)
            Set<BlockPos> seen = new HashSet<>();
            for (BlockSnapshot snapshot : ((BlockEvent.MultiPlaceEvent) event).getReplacedBlockSnapshots()) {
                if (seen.add(snapshot.getPos())) apply(event.getWorld(), pending, snapshot.getPos());
            }
        } else {
            apply(event.getWorld(), pending, event.getPos());
        }
    }

    @SubscribeEvent
    public void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        World world = event.getWorld();
        if (world.isRemote) return;
        Map<Long, IBlockState> held = settling.get(world);
        if (held == null) return;
        BlockPos pos = event.getPos();
        long key = key(pos.getX(), pos.getY(), pos.getZ());
        IBlockState desired = held.get(key);
        if (desired == null) return;
        IBlockState current = world.getBlockState(pos);
        if (current.getBlock() != desired.getBlock()) {
            held.remove(key);
            return;
        }
        IBlockState next = merge(current, desired);
        if (next != null) world.setBlockState(pos, next, 2);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) settling.clear();
    }

    /** The placed block of the position with the intent's state applied; used by the place event. */
    private void apply(World world, Map<Long, Intent> pending, BlockPos pos) {
        long key = key(pos.getX(), pos.getY(), pos.getZ());
        Map<Long, IBlockState> held = settling.get(world);
        if (held != null) held.remove(key);
        if (pending == null) return;
        Intent intent;
        synchronized (pending) {
            intent = pending.remove(key);
        }
        if (intent == null || !ServerConfig.accuratePlacement || System.nanoTime() - intent.time > LIFETIME_NANOS) return;
        IBlockState current = world.getBlockState(pos);
        IBlockState desired = StateStrings.parse(intent.state);
        if (desired == null || !sameKind(desired.getBlock(), current.getBlock())) return;
        IBlockState next = merge(current, desired);
        if (next == null) return;
        world.setBlockState(pos, next, 2);
        settling.computeIfAbsent(world, w -> new HashMap<>()).put(key, desired);
        if (next.getBlock() instanceof BlockChest) pairChest(world, pos, next);
    }

    /** The same block, or its lit / powered twin (an item places the unlit repeater where a schematic has the lit one). */
    private static boolean sameKind(Block wanted, Block placed) {
        return StateMatch.switchedPair(wanted, placed);
    }

    /**
     * What BlockChest.onBlockPlacedBy does for a chest placed with this facing: a chest of the same kind beside it, on the
     * sides the facing allows a double chest, turns the same way.
     */
    private static void pairChest(World world, BlockPos pos, IBlockState chest) {
        EnumFacing facing = chest.getValue(BlockChest.FACING);
        EnumFacing[] sides = facing.getAxis() == EnumFacing.Axis.X ? new EnumFacing[] {EnumFacing.NORTH, EnumFacing.SOUTH}
            : new EnumFacing[] {EnumFacing.WEST, EnumFacing.EAST};
        for (EnumFacing side : sides) {
            BlockPos other = pos.offset(side);
            IBlockState state = world.getBlockState(other);
            if (state.getBlock() != chest.getBlock()) continue;
            if (state.getValue(BlockChest.FACING) != facing) world.setBlockState(other, state.withProperty(BlockChest.FACING, facing), 3);
            return;
        }
    }

    /**
     * The current state with the desired values of the settable properties (matched by name), or null when nothing
     * changes or the change is not allowed (it would drop other items).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static IBlockState merge(IBlockState current, IBlockState desired) {
        Block block = current.getBlock();
        IBlockState next = current;
        for (IProperty property : current.getPropertyKeys()) {
            boolean quartzAxis = block instanceof BlockQuartz && property == BlockQuartz.VARIANT;
            if (!settable(block, property.getName()) && !quartzAxis) continue;
            Comparable value = null;
            for (IProperty candidate : desired.getPropertyKeys()) {
                if (!candidate.getName().equals(property.getName())) continue;
                Optional parsed = property.parseValue(candidate.getName(desired.getValue(candidate)));
                if (parsed.isPresent()) value = (Comparable) parsed.get();
                break;
            }
            if (value == null) continue;
            if (quartzAxis && !(pillar(value) && pillar(current.getValue(property)))) continue;
            next = next.withProperty(property, value);
        }
        if (next == current) return null;
        Random random = new Random(0);
        if (block.damageDropped(next) != block.damageDropped(current)) return null;
        if (block.getItemDropped(next, new Random(0), 0) != block.getItemDropped(current, random, 0)) return null;
        if (block.quantityDropped(next, 0, new Random(0)) != block.quantityDropped(current, 0, new Random(0))) return null;
        return next;
    }

    private static boolean pillar(Object variant) {
        return variant == BlockQuartz.EnumType.LINES_X || variant == BlockQuartz.EnumType.LINES_Y || variant == BlockQuartz.EnumType.LINES_Z;
    }
}
