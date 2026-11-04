// The printer: places what the loaded Litematica placements want around the player, after Schematica Upgraded's
// SchematicPrinter (Lunatrius / HackerRouter / Useleess, MIT). "What should be here" is Litematica's schematic world.
package com.useleess.litematicaupgraded.printer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.block.Block;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ClickType;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.useleess.litematicaupgraded.litematica.materials.MaterialCache;
import com.useleess.litematicaupgraded.litematica.util.EntityUtils;
import com.useleess.litematicaupgraded.litematica.world.SchematicWorldHandler;
import com.useleess.litematicaupgraded.malilib.util.world.BlockState;
import com.useleess.litematicaupgraded.server.ServerFeatures;

public final class Printer {
    public static final Printer INSTANCE = new Printer();

    /** The blocks placed so far, in order (for tests and the debug screen); the newest 4096. */
    public static final List<BlockPos> PLACED = new ArrayList<>();
    /** The containers whose filter items were filled in so far (for tests); the newest 256. */
    public static final List<BlockPos> FILLED = ContainerFiller.FILLED;

    /** How the last container fill ended: "filled at ..." or why it stopped early (tests). */
    public static String lastFillEnd() {
        return ContainerFiller.lastEnd;
    }

    private final Map<Long, Long> cooldowns = new HashMap<>();
    /** Positions waiting for an item (until-tick, tier): they keep holding the higher tiers back while they rest. */
    private final Map<Long, long[]> noItem = new HashMap<>();
    private long tick, heldSince = -1;
    private boolean slotsSynced;

    private final BlockMiner miner = new BlockMiner();
    private final ContainerFiller filler = new ContainerFiller();
    /** The block being mined, the hotbar slot of the tool picked for it, and how to give the player's slots back afterwards. */
    private BlockPos mining;
    private int toolSlot = -1, toolRestoreSlot, toolSwappedFrom = -1;
    /** The scaffold blocks placed under fence gates, waiting for their gate to be built so they can be mined away. */
    private final Set<BlockPos> scaffolds = new HashSet<>();
    private World scaffoldWorld;

    private Printer() {}

    private enum Result { DONE, NO_ITEM, FAILED }

    private enum Kind { PLACE, CLICK, SNOW, TOOL, FLUID, FILL }

    private static final class Candidate {
        final BlockPos pos;
        final IBlockState target;
        final int tier;
        final Kind kind;
        /** CLICK: the number of clicks; TOOL: unused. */
        final int clicks;
        /** TOOL: the block to click. */
        BlockPos clickPos;
        /** FILL: the items of the schematic's container. */
        ItemStack[] wanted;

        Candidate(BlockPos pos, IBlockState target, int tier, Kind kind, int clicks) {
            this.pos = pos; this.target = target; this.tier = tier; this.kind = kind; this.clicks = clicks;
        }
    }

    public boolean isEnabled() {
        return PrinterConfigs.PRINTER_ENABLED.getBooleanValue() && ServerFeatures.printerAllowed;
    }

    /** Whether the open container screen is the printer's own, opened to fill it. */
    boolean fillingContainer() {
        return filler.busy();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (!slotsSynced) {
            // Litematica fills its pick block slot list only when a config file is loaded: a fresh install would never pick a block
            slotsSynced = true;
            com.useleess.litematicaupgraded.litematica.util.PickBlockUtils.setPickBlockUsableSlots(
                com.useleess.litematicaupgraded.litematica.config.Configs.Generic.PICK_BLOCK_USABLE_SLOTS.getValue());
        }
        if (isEnabled() && PrinterConfigs.STOP_AFTER_DEATH.getBooleanValue() && mc.player != null && (mc.player.getHealth() <= 0 || mc.player.isDead)) {
            PrinterConfigs.PRINTER_ENABLED.setBooleanValue(false);
        }
        RelaxedPlacement.tick(mc.world);
        ManualMining.tick(mc);
        if (!isEnabled()) {
            ManualMining.forget();
            if (!DestroyAssist.active()) stopMining();
            filler.clear();
            return;
        }
        if (mc.player == null || mc.world == null || mc.playerController == null) return;
        if (filler.busy()) {
            // the container's screen is open: the filler has the tick
            filler.tick(mc, mc.player);
            return;
        }
        if (mc.currentScreen != null) return;
        if (PrinterConfigs.PAUSE_WHILE_MINING.getBooleanValue() && ManualMining.active()) {
            stopMining();
            return;
        }
        try {
            print(mc, mc.player, mc.world);
        } catch (RuntimeException e) {
            com.useleess.litematicaupgraded.litematica.Litematica.LOGGER.error("The printer failed", e);
        }
    }

    /** One pass: the blocks around the player the schematic wants and the world lacks, placed in order. */
    int print(Minecraft mc, EntityPlayerSP player, WorldClient world) {
        World schematic = SchematicWorldHandler.getSchematicWorld();
        if (schematic == null || DestroyAssist.active()) return 0;
        tick++;
        cooldowns.values().removeIf(until -> until <= tick);
        noItem.values().removeIf(wait -> wait[0] <= tick);
        int range = PrinterRange.printer();
        double mineRange = PrinterRange.mining(range);
        double eyeX = player.posX, eyeY = player.posY + player.getEyeHeight(), eyeZ = player.posZ;
        if (continueMining(world, eyeX, eyeY, eyeZ, mineRange) || removeScaffold(world, eyeX, eyeY, eyeZ, mineRange)) return 0;
        int bx = MathHelper.floor(eyeX), by = MathHelper.floor(eyeY), bz = MathHelper.floor(eyeZ);
        List<int[]> offsets = PrinterIteration.offsets(range, PrinterConfigs.SHAPE.getValue(), PrinterConfigs.AXIS_ORDER.getValue(), false, false, false);
        boolean ordered = PrinterConfigs.REDSTONE_ORDER.getBooleanValue();
        boolean breakExtra = PrinterConfigs.BREAK_EXTRA_BLOCK.getBooleanValue();
        boolean breakWrong = PrinterConfigs.BREAK_WRONG_BLOCK.getBooleanValue();
        boolean breakWrongState = PrinterConfigs.BREAK_WRONG_STATE.getBooleanValue();
        boolean fill = PrinterConfigs.FILL_CONTAINERS.getBooleanValue();
        List<Candidate> candidates = new ArrayList<>();
        BlockPos mine = null;
        boolean mineNoSilk = false;
        for (int[] offset : offsets) {
            int x = bx + offset[0], y = by + offset[1], z = bz + offset[2];
            if (y < 0 || y > 255) continue;
            double dx = x + 0.5 - eyeX, dy = y + 0.5 - eyeY, dz = z + 0.5 - eyeZ;
            double distance = dx * dx + dy * dy + dz * dz;
            if (distance > (double) range * range) continue;
            BlockPos pos = new BlockPos(x, y, z);
            if (cooldowns.containsKey(key(pos)) || !world.isBlockLoaded(pos) || ManualMining.skipped(pos) || !PrinterArea.allowed(x, y, z)) continue;
            IBlockState target = schematic.getBlockState(pos);
            IBlockState real = world.getBlockState(pos);
            if (target.getBlock().isAir(target, schematic, pos)) {
                // an extra block: only inside a placement, where the schematic has air on purpose
                if (mine == null && breakExtra && distance <= mineRange * mineRange && PrinterArea.insidePlacement(x, y, z) && breakWanted(world, schematic, pos, true, false, false)) mine = pos;
                continue;
            }
            int tier = ordered ? PrinterOrder.tier(target) : 0;
            if (FluidPrinter.isFluid(target.getBlock())) {
                // only sources are placed; the flowing water follows from them
                if (!FluidPrinter.wantsSource(schematic, pos) || FluidPrinter.matches(schematic, world, pos)) continue;
                if (IceWater.isIce(real)) {
                    if (mine == null && distance <= mineRange * mineRange && PrinterConfigs.ICE_WATER.getBooleanValue()) {
                        mine = pos;
                        mineNoSilk = true;
                    }
                } else if (real.getBlock().isAir(real, world, pos) || real.getBlock().isReplaceable(world, pos) || real.getMaterial().isLiquid()) {
                    candidates.add(new Candidate(pos, target, 0, Kind.FLUID, 0));
                }
                continue;
            }
            if (StateMatch.built(real, target)) {
                int clicks = StateClicks.clicks(real, target);
                if (clicks > 0) {
                    candidates.add(new Candidate(pos, target, tier, Kind.CLICK, clicks));
                } else if (fill && distance <= ContainerFiller.REACH * ContainerFiller.REACH) {
                    ItemStack[] wanted = filler.wanted(world, schematic, pos);
                    if (wanted != null) {
                        Candidate candidate = new Candidate(pos, target, tier, Kind.FILL, 0);
                        candidate.wanted = wanted;
                        candidates.add(candidate);
                    }
                }
                continue;
            }
            if (PrinterConfigs.SNOW_LAYERS.getBooleanValue() && SnowLayers.missing(real, target) > 0) {
                candidates.add(new Candidate(pos, target, tier, Kind.SNOW, 0));
                continue;
            }
            BlockPos toolClick = ToolUse.click(world, target, real, pos);
            if (toolClick != null) {
                Candidate candidate = new Candidate(pos, target, tier, Kind.TOOL, 0);
                candidate.clickPos = toolClick;
                candidates.add(candidate);
                continue;
            }
            if (!real.getBlock().isAir(real, world, pos) && !real.getBlock().isReplaceable(world, pos) && !real.getMaterial().isLiquid()) {
                // only air and fluids are filled in; a wrong block is mined first when the settings say so, else left alone
                if (mine == null && distance <= mineRange * mineRange && breakWanted(world, schematic, pos, breakExtra, breakWrong, breakWrongState)) mine = pos;
                continue;
            }
            candidates.add(new Candidate(pos, target, tier, Kind.PLACE, 0));
        }
        if (mine != null && beginMining(world, mine, mineNoSilk)) {
            heldSince = -1;
            return 0;
        }
        if (candidates.isEmpty() && noItem.isEmpty()) {
            heldSince = -1;
            return 0;
        }
        candidates.sort((a, b) -> Integer.compare(a.tier, b.tier)); // stable: the iteration order stays inside a tier
        int perTick = PrinterConfigs.BLOCKS_PER_TICK.getIntegerValue();
        int lowestWaiting = Integer.MAX_VALUE, done = 0;
        for (long[] wait : noItem.values()) lowestWaiting = Math.min(lowestWaiting, (int) wait[1]);
        boolean held = false;
        for (Candidate candidate : candidates) {
            if (done >= perTick) break;
            if (!PrinterOrder.mayPlace(candidate.tier, lowestWaiting, heldSince < 0 ? 0 : tick - heldSince)) {
                held = true;
                continue;
            }
            Result result = execute(mc, player, world, schematic, candidate);
            if (result == Result.DONE) {
                cooldowns.put(key(candidate.pos), tick + PrinterConfigs.COOLDOWN.getIntegerValue());
                noItem.remove(key(candidate.pos));
                done++;
                if (filler.busy()) break;
            } else if (result == Result.NO_ITEM) {
                // a missing item holds the higher tiers back, for a while
                lowestWaiting = Math.min(lowestWaiting, candidate.tier);
                noItem.put(key(candidate.pos), new long[] {tick + 5, candidate.tier});
                cooldowns.put(key(candidate.pos), tick + 5);
            } else {
                // no click gives the state yet (a neighbour may come): look again later, the simulation is not free
                cooldowns.put(key(candidate.pos), tick + 10);
            }
        }
        if (!held) heldSince = -1;
        else if (heldSince < 0) heldSince = tick;
        return done;
    }

    private Result execute(Minecraft mc, EntityPlayerSP player, WorldClient world, World schematic, Candidate candidate) {
        switch (candidate.kind) {
            case CLICK: return clickBlock(mc, player, world, candidate);
            case SNOW: return snowLayer(mc, player, world, candidate);
            case TOOL: return toolUse(mc, player, world, candidate);
            case FLUID: return placeFluid(mc, player, world, candidate);
            case FILL: return filler.start(mc, player, candidate.pos, candidate.wanted) ? Result.DONE : Result.FAILED;
            default: return placeBlock(mc, player, world, schematic, candidate);
        }
    }

    private Result placeBlock(Minecraft mc, EntityPlayerSP player, WorldClient world, World schematic, Candidate candidate) {
        // snow: one layer is placed like any block, the rest is clicked on afterwards
        IBlockState target = SnowLayers.firstLayer(candidate.target);
        if (RelaxedPlacement.waitingFor(candidate.pos)) return Result.DONE;
        if (Scaffold.needed(world, schematic, candidate.pos, target)) {
            // the server part places the gate in the air; or Carpet's rule lets it stand there; else a temporary block goes under it
            if (ServerAssist.air(target)) return placeInAir(mc, player, candidate.pos, target);
            if (!RelaxedPlacement.allowed(world, target.getBlock()) && PrinterConfigs.SCAFFOLD.getBooleanValue()) return placeScaffold(mc, player, world, candidate.pos.down());
        }
        ItemStack required = MaterialCache.getInstance().getRequiredBuildItemForState(BlockState.of(candidate.target));
        if (required == null || required.isEmpty()) return Result.FAILED;
        Result result = placeItem(mc, player, world, required, target, candidate.pos);
        if (result == Result.DONE) {
            PLACED.add(candidate.pos);
            if (PLACED.size() > 4096) PLACED.remove(0);
        }
        return result;
    }

    /** Places the item so that the block at pos gets the state; NO_ITEM when the player has none, FAILED when no click gives it. */
    private Result placeItem(Minecraft mc, EntityPlayerSP player, WorldClient world, ItemStack required, IBlockState state, BlockPos pos) {
        EnumHand hand = select(mc, player, stack -> sameItem(stack, required));
        if (hand == null) {
            // a carried shulker box may hold it (server part); the item is there for a later pass
            ServerAssist.restock(player, required);
            return Result.NO_ITEM;
        }
        // with accurate placement any click that makes the block does: the server sets the schematic state (AccuratePlacement)
        PlacementSolver.Solution solution = ServerAssist.accurate(state.getBlock())
            ? PlacementSolver.solveAccurate(world, player, player.getHeldItem(hand), state, pos) : null;
        if (solution != null) {
            ServerAssist.announce(pos, state);
        } else {
            solution = PlacementSolver.solve(world, player, player.getHeldItem(hand), state, pos,
                PrinterConfigs.FAKE_ROTATION.getBooleanValue(), PrinterConfigs.FAKE_POSITION.getBooleanValue());
        }
        if (solution == null) return Result.FAILED;
        return click(mc, player, world, solution, hand, true) ? Result.DONE : Result.FAILED;
    }

    /** Asks the server part to place the held block in the air (a fence gate over nothing); the block arrives with the server's update. */
    private Result placeInAir(Minecraft mc, EntityPlayerSP player, BlockPos pos, IBlockState state) {
        if (ServerAssist.airWaiting(pos)) return Result.DONE;
        ItemStack required = MaterialCache.getInstance().getRequiredBuildItemForState(BlockState.of(state));
        if (required == null || required.isEmpty()) return Result.FAILED;
        if (select(mc, player, stack -> sameItem(stack, required)) == null) {
            ServerAssist.restock(player, required);
            return Result.NO_ITEM;
        }
        ServerAssist.airPlace(player, pos, state);
        PLACED.add(pos);
        if (PLACED.size() > 4096) PLACED.remove(0);
        return Result.DONE;
    }

    /** The temporary block under a fence gate. */
    private Result placeScaffold(Minecraft mc, EntityPlayerSP player, WorldClient world, BlockPos pos) {
        Block material = Scaffold.material(player.inventory);
        if (material == null) return Result.NO_ITEM;
        Result result = placeItem(mc, player, world, new ItemStack(material), material.getDefaultState(), pos);
        if (result == Result.DONE) {
            if (scaffoldWorld != world) scaffolds.clear();
            scaffoldWorld = world;
            scaffolds.add(pos.toImmutable());
        }
        return result;
    }

    /** Mines a scaffold away once the gate above it stands; true when it started to. */
    private boolean removeScaffold(WorldClient world, double eyeX, double eyeY, double eyeZ, double range) {
        if (scaffolds.isEmpty()) return false;
        if (world != scaffoldWorld) {
            scaffolds.clear();
            return false;
        }
        for (Iterator<BlockPos> it = scaffolds.iterator(); it.hasNext();) {
            BlockPos pos = it.next();
            if (world.isAirBlock(pos)) {
                it.remove();
                continue;
            }
            double dx = pos.getX() + 0.5 - eyeX, dy = pos.getY() + 0.5 - eyeY, dz = pos.getZ() + 0.5 - eyeZ;
            if (Scaffold.done(world, pos) && dx * dx + dy * dy + dz * dz <= range * range && beginMining(world, pos, false)) return true;
        }
        return false;
    }

    /** A bucket of the schematic's fluid, or ice that is mined into a water source. */
    private Result placeFluid(Minecraft mc, EntityPlayerSP player, WorldClient world, Candidate candidate) {
        Fluid fluid = FluidPrinter.fluid(candidate.target.getBlock());
        if (PrinterConfigs.FLUIDS.getBooleanValue() && FluidPrinter.hasBucket(player, fluid)) {
            return FluidPrinter.place(mc, fluid, candidate.pos, stack -> select(mc, player, other -> sameItem(other, stack)) != null) ? Result.DONE : Result.FAILED;
        }
        if (PrinterConfigs.ICE_WATER.getBooleanValue() && fluid == FluidRegistry.WATER && IceWater.works(world, candidate.pos, player.capabilities.isCreativeMode)) {
            return placeItem(mc, player, world, new ItemStack(Blocks.ICE), Blocks.ICE.getDefaultState(), candidate.pos);
        }
        return Result.NO_ITEM;
    }

    /** Farmland is tilled with a hoe, a nether portal lit with flint and steel. */
    private Result toolUse(Minecraft mc, EntityPlayerSP player, WorldClient world, Candidate candidate) {
        EnumHand hand = select(mc, player, stack -> ToolUse.accepts(candidate.target, stack));
        if (hand == null) return Result.NO_ITEM;
        BlockPos at = candidate.clickPos;
        PlacementSolver.Solution solution = new PlacementSolver.Solution(at, EnumFacing.UP, new Vec3d(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5), null, null);
        return click(mc, player, world, solution, hand, false) ? Result.DONE : Result.FAILED;
    }

    /** One more layer on the snow: the snow item on the top of the snow block. */
    private Result snowLayer(Minecraft mc, EntityPlayerSP player, WorldClient world, Candidate candidate) {
        ItemStack required = MaterialCache.getInstance().getRequiredBuildItemForState(BlockState.of(candidate.target));
        if (required == null || required.isEmpty()) return Result.FAILED;
        EnumHand hand = select(mc, player, stack -> sameItem(stack, required));
        if (hand == null) {
            ServerAssist.restock(player, required);
            return Result.NO_ITEM;
        }
        Vec3d hit = new Vec3d(candidate.pos.getX() + 0.5, candidate.pos.getY() + SnowLayers.top(world.getBlockState(candidate.pos)), candidate.pos.getZ() + 0.5);
        return click(mc, player, world, new PlacementSolver.Solution(candidate.pos, EnumFacing.UP, hit, null, null), hand, true) ? Result.DONE : Result.FAILED;
    }

    /**
     * Puts the wanted item in the main hand: selects its hotbar slot, or swaps it from the inventory into the selected slot.
     * Null when the player has none. Matches item and damage value, not NBT.
     */
    private static EnumHand select(Minecraft mc, EntityPlayerSP player, Predicate<ItemStack> wanted) {
        if (wanted.test(player.getHeldItemMainhand())) return EnumHand.MAIN_HAND;
        for (int slot = 0; slot < 9; slot++) {
            if (wanted.test(player.inventory.getStackInSlot(slot))) {
                player.inventory.currentItem = slot;
                mc.playerController.updateController();
                return EnumHand.MAIN_HAND;
            }
        }
        for (int slot = 9; slot < 36; slot++) {
            if (wanted.test(player.inventory.getStackInSlot(slot))) {
                // container slot numbers: the main inventory 9-35 are the same, the hotbar is 36-44
                mc.playerController.windowClick(player.inventoryContainer.windowId, slot, player.inventory.currentItem, ClickType.SWAP, player);
                return wanted.test(player.getHeldItemMainhand()) ? EnumHand.MAIN_HAND : null;
            }
        }
        return null;
    }

    private static boolean sameItem(ItemStack have, ItemStack want) {
        return !have.isEmpty() && have.getItem() == want.getItem() && have.getMetadata() == want.getMetadata();
    }

    /** Fixes a repeater's delay, a comparator's mode or a lever's switch by right clicking the block as often as needed. */
    private Result clickBlock(Minecraft mc, EntityPlayerSP player, WorldClient world, Candidate candidate) {
        return clickState(mc, player, world, candidate.pos, candidate.target, candidate.clicks) ? Result.DONE : Result.FAILED;
    }

    /** Clicks the built block at pos the given times, to give it the target's delay, mode, switch or open state (StateClicks). */
    static boolean clickState(Minecraft mc, EntityPlayerSP player, WorldClient world, BlockPos pos, IBlockState target, int clicks) {
        Vec3d hit = new Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        PlacementSolver.Solution solution = new PlacementSolver.Solution(pos, EnumFacing.UP, hit, StateClicks.look(target), null);
        for (int i = 0; i < clicks; i++) {
            if (!click(mc, player, world, solution, EnumHand.MAIN_HAND, false)) return false;
        }
        return true;
    }

    // ---- mining: wrong, extra and wrong-state blocks, scaffolds, ice, the destroy assist ----

    /** Whether the block is one the placement wants gone, for the destroy assist: extra, wrong and wrong-state blocks all count. */
    boolean wantedGone(World world, BlockPos pos) {
        World schematic = SchematicWorldHandler.getSchematicWorld();
        return schematic != null && breakWanted(world, schematic, pos, true, true, true);
    }

    /**
     * Blocks where the schematic has air (inside a placement), another block, or the same block in another state may be
     * mined first. Replaceable blocks, fluids, blocks a click or a tool fixes and blocks a slab completes are left to the placement.
     */
    boolean breakWanted(World world, World schematic, BlockPos pos, boolean extra, boolean wrong, boolean wrongState) {
        if (!extra && !wrong && !wrongState) return false;
        IBlockState real = world.getBlockState(pos);
        if (real.getBlock().isAir(real, world, pos) || FluidPrinter.isFluid(real.getBlock()) || real.getBlockHardness(world, pos) < 0) return false;
        // scaffolds are only mined once their gate stands
        if (world == scaffoldWorld && scaffolds.contains(pos)) return false;
        IBlockState target = schematic.getBlockState(pos);
        if (target.getBlock().isAir(target, schematic, pos)) {
            return extra && PrinterArea.insidePlacement(pos.getX(), pos.getY(), pos.getZ());
        }
        if (FluidPrinter.isFluid(target.getBlock()) || StateMatch.built(real, target)) return false;
        if (target.getBlock() != real.getBlock()) {
            return wrong && !StateMatch.switchedPair(target.getBlock(), real.getBlock()) && !ToolUse.fixable(world, target, real, pos)
                && !real.getBlock().isReplaceable(world, pos) && !completesSlab(target, real);
        }
        return wrongState && SnowLayers.missing(real, target) == 0;
    }

    /** A half slab where the schematic has the double slab of it: a second slab completes it. */
    private static boolean completesSlab(IBlockState target, IBlockState real) {
        return target.getBlock() instanceof BlockSlab && ((BlockSlab) target.getBlock()).isDouble()
            && real.getBlock() instanceof BlockSlab && !((BlockSlab) real.getBlock()).isDouble();
    }

    /** Starts mining a block with the best tool; false when it could not start. noSilk: a Silk Touch tool is never used (ice mined for its water). */
    boolean beginMining(World world, BlockPos pos, boolean noSilk) {
        EntityPlayerSP player = Minecraft.getMinecraft().player;
        int previous = player.inventory.currentItem;
        boolean switched = selectTool(player, world, pos, noSilk);
        if (noSilk && IceWater.silk(player.getHeldItemMainhand())) {
            restoreTool(player, previous, switched);
            return false;
        }
        BlockMiner.State state = miner.start(pos, EnumFacing.UP);
        if (state == BlockMiner.State.IDLE) {
            restoreTool(player, previous, switched);
            return false;
        }
        player.swingArm(EnumHand.MAIN_HAND);
        if (state == BlockMiner.State.MINING) {
            mining = pos;
            if (switched) {
                toolSlot = player.inventory.currentItem;
                toolRestoreSlot = previous;
            }
        } else {
            restoreTool(player, previous, switched);
        }
        return true;
    }

    /** Keeps mining the started block; false when there is none (any more) and the pass may place. */
    boolean continueMining(World world, double eyeX, double eyeY, double eyeZ, double range) {
        if (mining == null) return false;
        BlockPos pos = mining;
        double dx = pos.getX() + 0.5 - eyeX, dy = pos.getY() + 0.5 - eyeY, dz = pos.getZ() + 0.5 - eyeZ;
        Minecraft mc = Minecraft.getMinecraft();
        if (world.isAirBlock(pos) || dx * dx + dy * dy + dz * dz > range * range) {
            stopMining();
            return false;
        }
        if (toolSlot >= 0) mc.player.inventory.currentItem = toolSlot;
        if (miner.tick() != BlockMiner.State.MINING) {
            stopMining();
            return false;
        }
        return true;
    }

    void stopMining() {
        miner.abort();
        mining = null;
        Minecraft mc = Minecraft.getMinecraft();
        if (toolSlot >= 0 && mc.player != null) {
            mc.player.inventory.currentItem = toolSlot; // a swapped-in tool is swapped back from where it went
            restoreTool(mc.player, toolRestoreSlot, true);
        }
        toolSlot = -1;
    }

    /** Gives the player's slots back as before the tool was picked. */
    private void restoreTool(EntityPlayerSP player, int previous, boolean switched) {
        if (!switched) return;
        if (toolSwappedFrom >= 0) {
            Minecraft.getMinecraft().playerController.windowClick(player.inventoryContainer.windowId, toolSwappedFrom, player.inventory.currentItem, ClickType.SWAP, player);
            toolSwappedFrom = -1;
        }
        player.inventory.currentItem = previous;
    }

    /**
     * Holds the item of the inventory that mines the block fastest (harvest level and enchantments included): a hotbar
     * slot is selected, another slot is swapped into the selected one. The held item stays when nothing is faster. True
     * when the held slot or item changed.
     */
    private boolean selectTool(EntityPlayerSP player, World world, BlockPos pos, boolean noSilk) {
        InventoryPlayer inventory = player.inventory;
        int current = inventory.currentItem;
        boolean leave = noSilk && IceWater.silk(inventory.mainInventory.get(current));
        if (!leave && (!PrinterConfigs.AUTO_TOOL.getBooleanValue() || Minecraft.getMinecraft().playerController.isInCreativeMode())) return false;
        IBlockState state = world.getBlockState(pos);
        float best = leave ? -1 : strength(player, world, state, pos, inventory.mainInventory.get(current));
        int bestSlot = -1;
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inventory.mainInventory.get(slot);
            if (slot == current || stack.isEmpty() || noSilk && IceWater.silk(stack)) continue;
            float strength = strength(player, world, state, pos, stack);
            if (strength > best) {
                best = strength;
                bestSlot = slot;
            }
        }
        if (bestSlot < 0) return false;
        if (bestSlot >= 9) {
            Minecraft.getMinecraft().playerController.windowClick(player.inventoryContainer.windowId, bestSlot, current, ClickType.SWAP, player);
            toolSwappedFrom = bestSlot;
        } else {
            inventory.currentItem = bestSlot;
        }
        return true;
    }

    /** The block's mining progress per tick with the stack in hand (the held slot is swapped only on the client side). */
    private static float strength(EntityPlayerSP player, World world, IBlockState state, BlockPos pos, ItemStack stack) {
        InventoryPlayer inventory = player.inventory;
        ItemStack held = inventory.mainInventory.get(inventory.currentItem);
        inventory.mainInventory.set(inventory.currentItem, stack);
        try {
            return state.getPlayerRelativeBlockHardness(player, world, pos);
        } catch (RuntimeException e) {
            return -1;
        } finally {
            inventory.mainInventory.set(inventory.currentItem, held);
        }
    }

    /**
     * The click of a solution: the fake position and rotation are reported to the server just before it and the real ones
     * right after, so the server places the block as the schematic has it although it has no mod.
     */
    static boolean click(Minecraft mc, EntityPlayerSP player, WorldClient world, PlacementSolver.Solution solution, EnumHand hand, boolean sneak) {
        float yaw = player.rotationYaw, pitch = player.rotationPitch;
        Vec3d real = solution.stand != null ? FakeStand.enter(player, solution.stand) : null;
        if (solution.look != null) turn(player, solution.look.yaw, solution.look.pitch);
        boolean sneaking = sneak && EntityUtils.setFakedSneakingState(true);
        try {
            EnumActionResult result = solution.relaxed ? RelaxedPlacement.send(player, solution, hand) : PrinterRange.click(player, solution.clicked, solution.side, solution.hit, hand);
            if (result == EnumActionResult.SUCCESS) player.swingArm(hand);
            return result == EnumActionResult.SUCCESS;
        } finally {
            if (sneaking) EntityUtils.setFakedSneakingState(false);
            if (solution.look != null) turn(player, yaw, pitch);
            if (real != null) FakeStand.leave(player, real);
        }
    }

    /** Turns the player and reports the rotation to the server. */
    static void turn(EntityPlayerSP player, float yaw, float pitch) {
        player.rotationYaw = yaw;
        player.rotationPitch = pitch;
        player.connection.sendPacket(new CPacketPlayer.Rotation(yaw, pitch, player.onGround));
    }

    private static long key(BlockPos pos) {
        return pos.toLong();
    }
}
