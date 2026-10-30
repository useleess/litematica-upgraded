package com.useleess.litematicaupgraded.selftest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameType;
import net.minecraft.world.World;

import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.materials.MaterialCache;
import com.useleess.litematicaupgraded.litematica.schematic.LoadedSchematic;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;
import com.useleess.litematicaupgraded.litematica.world.SchematicWorldHandler;
import com.useleess.litematicaupgraded.malilib.util.world.BlockState;
import com.useleess.litematicaupgraded.printer.Printer;
import com.useleess.litematicaupgraded.printer.PrinterConfigs;
import com.useleess.litematicaupgraded.printer.StateClicks;
import com.useleess.litematicaupgraded.printer.StateMatch;

/**
 * One printer test: a survival player on a stone platform high in the sky of the test server (so the world's terrain
 * never matters), a schematic written from the blocks the test lists and placed on the platform, items given by server
 * commands, and a check of the real block states in the client world (which the server's block updates have the last
 * word on). The printer is only turned on once the placement is in the schematic world and the items are in the inventory.
 */
final class Scenario {
    /** A check: null when it holds, else what is wrong. */
    interface Check {
        String problem(Runner.Context c, Scenario s);
    }

    private static int counter;
    private static int baseX, baseZ;

    final String id;
    /** Blocks of the schematic, at positions relative to the origin (all >= 0). */
    final Map<BlockPos, IBlockState> build = new LinkedHashMap<>();
    /** Blocks put into the world before the printer starts (relative positions). */
    final Map<BlockPos, IBlockState> preplaced = new LinkedHashMap<>();
    /** Tile entity data of the schematic's blocks (relative positions): the id and the entity's tags. */
    final Map<BlockPos, net.minecraft.nbt.NBTTagCompound> tiles = new LinkedHashMap<>();
    final List<String> extraCommands = new ArrayList<>();
    final Map<Integer, Consumer<Runner.Context>> at = new TreeMap<>();
    boolean give = true;
    /** Whether the printer is turned on when the setup has arrived (a test that needs another order does it in at(0)). */
    boolean autoEnable = true;
    int range = 6, perTick = 2, stable = 10, maxTicks = 500;
    float yaw = 37, pitch = 20;
    /** Where the player stands, relative to the origin. */
    double standX = 1.5, standZ = 3.5;
    Check check = Scenario::allBuilt;
    /** A further condition for the setup to count as arrived (items that come by command, for example). */
    java.util.function.Predicate<Minecraft> extraReady;

    BlockPos origin;
    private SchematicPlacement placement;
    private int ticks, readyTick = -1, okTicks;
    private String lastProblem = "never checked";

    Scenario(String id) {
        this.id = id;
    }

    Scenario put(int x, int y, int z, IBlockState state) {
        build.put(new BlockPos(x, y, z), state);
        return this;
    }

    BlockPos world(BlockPos rel) {
        return origin.add(rel.getX(), rel.getY(), rel.getZ());
    }

    void command(Minecraft mc, String text) {
        mc.player.sendChatMessage("/" + text);
    }

    /** The test's setup step: moves to a free site, builds the platform, loads the placement, gives the items. */
    void start(Runner.Context c) throws Exception {
        Minecraft mc = c.mc;
        if (counter == 0) {
            baseX = (int) Math.floor(mc.player.posX) + 8;
            baseZ = (int) Math.floor(mc.player.posZ) + 8;
        }
        int site = counter++ % 6;
        int sx = baseX + site * 14, sz = baseZ;
        origin = new BlockPos(sx, 101, sz);
        PrinterConfigs.PRINTER_ENABLED.setBooleanValue(false);
        resetSettings();
        PrinterConfigs.RANGE.setIntegerValue(range);
        PrinterConfigs.BLOCKS_PER_TICK.setIntegerValue(perTick);
        Printer.PLACED.clear();
        // sites repeat every 6 tests: a container filled at this spot by an earlier test must not count
        Printer.FILLED.clear();
        command(mc, "gamemode survival");
        command(mc, "clear @s");
        // items dropped by an earlier test on this site (a hopper would suck them up)
        command(mc, "kill @e[type=item,x=" + sx + ",y=101,z=" + sz + ",r=20]");
        command(mc, "fill " + (sx - 6) + " 101 " + (sz - 6) + " " + (sx + 10) + " 115 " + (sz + 10) + " air");
        command(mc, "fill " + (sx - 6) + " 100 " + (sz - 6) + " " + (sx + 10) + " 100 " + (sz + 10) + " stone");
        command(mc, "tp @s " + (sx + standX) + " 101 " + (sz + standZ) + " " + yaw + " " + pitch);
        for (Map.Entry<BlockPos, IBlockState> e : preplaced.entrySet()) {
            BlockPos p = world(e.getKey());
            command(mc, "setblock " + p.getX() + " " + p.getY() + " " + p.getZ() + " "
                    + net.minecraft.block.Block.REGISTRY.getNameForObject(e.getValue().getBlock()) + " " + e.getValue().getBlock().getMetaFromState(e.getValue()));
        }
        if (give) giveItems(mc);
        for (String text : extraCommands) command(mc, text);
        Path dir = Paths.get("selftest-tmp").toAbsolutePath();
        Files.createDirectories(dir);
        Path file = dir.resolve(id.replace('/', '-') + ".litematic");
        LitematicWriter.write(file, build, tiles);
        LoadedSchematic loaded = LoadedSchematic.tryLoadSchematic(file).orElseThrow(() -> new IllegalStateException("cannot load the test schematic"));
        placement = SchematicPlacement.create(loaded, new com.useleess.litematicaupgraded.malilib.util.position.BlockPos(sx, 101, sz), "selftest-" + id, true);
        DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, false);
    }

    /** The test's placement (null once the test is over). */
    SchematicPlacement placement() {
        return placement;
    }

    private void giveItems(Minecraft mc) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, ItemStack> stacks = new HashMap<>();
        for (IBlockState state : build.values()) {
            ItemStack stack = MaterialCache.getInstance().getRequiredBuildItemForState(BlockState.of(state));
            if (stack == null || stack.isEmpty()) continue;
            String key = stack.getItem().getRegistryName() + " " + stack.getMetadata();
            counts.merge(key, 1, Integer::sum);
            stacks.put(key, stack);
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            // a stack holds at most the item's stack size (buckets: one)
            int max = stacks.get(e.getKey()).getMaxStackSize();
            command(mc, "give @s " + e.getKey().split(" ")[0] + " " + Math.min(max, e.getValue() + 2) + " " + e.getKey().split(" ")[1]);
        }
    }

    /** The poll step: waits for the setup to arrive, turns the printer on, runs the timed actions, waits for the check to hold. */
    boolean poll(Runner.Context c) {
        Minecraft mc = c.mc;
        ticks++;
        if (readyTick < 0) {
            String waiting = notReady(mc);
            if (waiting != null) {
                if (ticks > 200) throw new AssertionError("the setup did not arrive: " + waiting);
                return false;
            }
            readyTick = ticks;
            Printer.PLACED.clear();
            if (autoEnable) PrinterConfigs.PRINTER_ENABLED.setBooleanValue(true);
            Consumer<Runner.Context> first = at.get(0);
            if (first != null) first.accept(c);
            return false;
        }
        Consumer<Runner.Context> action = at.get(ticks - readyTick);
        if (action != null) action.accept(c);
        String problem = check.problem(c, this);
        lastProblem = problem == null ? "-" : problem;
        okTicks = problem == null ? okTicks + 1 : 0;
        if (okTicks >= stable) {
            finish();
            return true;
        }
        if (ticks - readyTick > maxTicks) {
            String message = "after " + maxTicks + " ticks: " + lastProblem + " (placed by the printer: " + Printer.PLACED.size() + ")";
            finish();
            throw new AssertionError(message);
        }
        return false;
    }

    /** Ticks since the setup arrived (0 right then), or -1 before. */
    int sinceReady() {
        return readyTick < 0 ? -1 : ticks - readyTick;
    }

    void finish() {
        net.minecraft.client.settings.KeyBinding.setKeyBindState(Minecraft.getMinecraft().gameSettings.keyBindAttack.getKeyCode(), false);
        PrinterConfigs.PRINTER_ENABLED.setBooleanValue(false);
        resetSettings();
        if (placement != null) {
            DataManager.getSchematicPlacementManager().removeSchematicPlacement(placement, true);
            placement = null;
        }
    }

    /** Every setting a test may change, back to its default (the tests do not leak into each other). */
    static void resetSettings() {
        PrinterConfigs.EASY_PLACE_FAKE_ROTATION.setBooleanValue(true);
        PrinterConfigs.FAKE_ROTATION.setBooleanValue(true);
        PrinterConfigs.FAKE_POSITION.setBooleanValue(true);
        com.useleess.litematicaupgraded.litematica.config.Configs.Generic.EASY_PLACE_CLICK_ADJACENT.setBooleanValue(false);
        PrinterConfigs.BREAK_WRONG_BLOCK.setBooleanValue(false);
        PrinterConfigs.BREAK_EXTRA_BLOCK.setBooleanValue(false);
        PrinterConfigs.BREAK_WRONG_STATE.setBooleanValue(false);
        PrinterConfigs.AUTO_TOOL.setBooleanValue(true);
        PrinterConfigs.USE_TOOLS.setBooleanValue(true);
        PrinterConfigs.SCAFFOLD.setBooleanValue(true);
        PrinterConfigs.FILL_CONTAINERS.setBooleanValue(false);
        PrinterConfigs.FLUIDS.setBooleanValue(true);
        PrinterConfigs.ICE_WATER.setBooleanValue(true);
        PrinterConfigs.SNOW_LAYERS.setBooleanValue(true);
        PrinterConfigs.SIGN_TEXT.setBooleanValue(true);
        PrinterConfigs.DESTROY_ASSIST.setBooleanValue(false);
        PrinterConfigs.LAYER_LIMIT.setBooleanValue(true);
        PrinterConfigs.STOP_AFTER_DEATH.setBooleanValue(true);
        com.useleess.litematicaupgraded.printer.DestroyAssist.stop();
        com.useleess.litematicaupgraded.malilib.util.position.LayerRange layers = DataManager.getRenderLayerRange();
        layers.setLayerMode(com.useleess.litematicaupgraded.malilib.config.value.LayerMode.ALL, false);
        layers.setAxis(com.useleess.litematicaupgraded.malilib.util.position.Direction.Axis.Y);
    }

    private String notReady(Minecraft mc) {
        if (mc.playerController.getCurrentGameType() != GameType.SURVIVAL) return "not in survival yet";
        double dx = mc.player.posX - (origin.getX() + standX), dz = mc.player.posZ - (origin.getZ() + standZ);
        if (dx * dx + dz * dz > 1 || Math.abs(mc.player.posY - 101) > 1.5) return "not at the site yet (" + mc.player.posX + ", " + mc.player.posY + ", " + mc.player.posZ + ")";
        if (!mc.world.getBlockState(origin.down()).getBlock().getRegistryName().toString().equals("minecraft:stone")) return "the platform is not there yet";
        for (Map.Entry<BlockPos, IBlockState> e : preplaced.entrySet()) {
            if (!StateMatch.placedMatches(mc.world.getBlockState(world(e.getKey())), e.getValue())) return "a pre-placed block is not there yet at " + e.getKey();
        }
        World schematic = SchematicWorldHandler.getSchematicWorld();
        if (schematic == null) return "no schematic world";
        for (Map.Entry<BlockPos, IBlockState> e : build.entrySet()) {
            if (schematic.getBlockState(world(e.getKey())).getBlock() != e.getValue().getBlock()) return "the schematic world lacks " + e.getKey();
        }
        if (extraReady != null && !extraReady.test(mc)) return "the extra setup condition does not hold yet";
        if (give) {
            boolean any = false;
            for (ItemStack stack : mc.player.inventory.mainInventory) any |= !stack.isEmpty();
            if (!any) return "no items yet";
        }
        return null;
    }

    /** Every block of the schematic stands in the world in the schematic state (levers and repeaters also as clicked). */
    static String allBuilt(Runner.Context c, Scenario s) {
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<BlockPos, IBlockState> e : s.build.entrySet()) {
            String problem = s.mismatch(c, e.getKey(), e.getValue());
            if (problem != null) wrong.add(problem);
        }
        return wrong.isEmpty() ? null : wrong.size() + " wrong: " + String.join("; ", wrong.subList(0, Math.min(3, wrong.size())));
    }

    /** What differs at a relative position, or null. */
    String mismatch(Runner.Context c, BlockPos rel, IBlockState target) {
        IBlockState real = c.mc.world.getBlockState(world(rel));
        if (!StateMatch.placedMatches(real, target)) return rel + " is " + real + ", wanted " + target;
        int clicks = StateClicks.clicks(real, target);
        if (clicks > 0) return rel + " still needs " + clicks + " clicks: " + real + " vs " + target;
        return null;
    }

    static boolean isAir(Runner.Context c, Scenario s, BlockPos rel) {
        return c.mc.world.getBlockState(s.world(rel)).getBlock() == net.minecraft.init.Blocks.AIR;
    }
}
