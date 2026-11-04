package com.useleess.litematicaupgraded.selftest;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDirectional;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.BlockRedstoneComparator;
import net.minecraft.block.BlockRedstoneRepeater;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.BlockWallSign;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockStoneSlab;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyDirection;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Loader;

import com.useleess.litematicaupgraded.litematica.Reference;
import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.gui.ConfigScreen;
import com.useleess.litematicaupgraded.litematica.util.EasyPlaceUtils;
import com.useleess.litematicaupgraded.malilib.action.ActionContext;
import com.useleess.litematicaupgraded.malilib.config.option.ConfigInfo;
import com.useleess.litematicaupgraded.malilib.config.value.LayerMode;
import com.useleess.litematicaupgraded.malilib.gui.config.BaseConfigScreen;
import com.useleess.litematicaupgraded.malilib.registry.Registry;
import com.useleess.litematicaupgraded.malilib.util.position.Direction;
import com.useleess.litematicaupgraded.malilib.util.position.LayerRange;
import com.useleess.litematicaupgraded.printer.DestroyAssist;
import com.useleess.litematicaupgraded.printer.Printer;
import com.useleess.litematicaupgraded.printer.PrinterConfigs;
import com.useleess.litematicaupgraded.printer.PrinterInit;
import com.useleess.litematicaupgraded.printer.SignTextPaste;

/** printer/, easyplace/ and title/: the printer places what the placements want, in survival, on a server without any mod. */
final class PrinterTests {
    private PrinterTests() {}

    static final IBlockState STONE = Blocks.STONE.getDefaultState();

    static IBlockState facing(Block block, EnumFacing facing) {
        IBlockState state = block.getDefaultState();
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (property instanceof PropertyDirection) return state.withProperty((PropertyDirection) property, facing);
        }
        throw new IllegalArgumentException(block + " has no facing");
    }

    private static int count(Runner.Context c, Block block) {
        int total = 0;
        Item item = Item.getItemFromBlock(block);
        for (ItemStack stack : c.mc.player.inventory.mainInventory) if (stack.getItem() == item) total += stack.getCount();
        return total;
    }

    private static void register(Runner.Registry r, Scenario s) {
        r.waitFor(s.id, s.maxTicks + 300, s::start, s::poll);
    }

    /** The BlockPos list index of a relative position in what the printer placed, or -1. */
    private static int placedIndex(Scenario s, BlockPos rel) {
        return Printer.PLACED.indexOf(s.world(rel));
    }

    /** A rotation test: the block (and the blocks it needs next to it) ends up as the schematic has it. */
    private static void rotation(Runner.Registry r, String name, IBlockState target, int x, int y, int z, Object... supports) {
        Scenario s = new Scenario("printer/rotation/" + name);
        for (int i = 0; i < supports.length; i += 4) {
            s.put((Integer) supports[i], (Integer) supports[i + 1], (Integer) supports[i + 2], (IBlockState) supports[i + 3]);
        }
        s.put(x, y, z, target);
        register(r, s);
    }

    static void register(Runner.Registry r) {
        // printer/place/: the blocks around the player, from the inventory
        Scenario p1 = new Scenario("printer/place/places-missing-blocks");
        for (int x = 0; x < 3; x++) for (int z = 0; z < 2; z++) p1.put(x, 0, z, (x + z) % 2 == 0 ? STONE : Blocks.COBBLESTONE.getDefaultState());
        p1.put(1, 1, 0, Blocks.PLANKS.getDefaultState());
        p1.check = (c, s) -> {
            String problem = Scenario.allBuilt(c, s);
            if (problem != null) return problem;
            int stone = count(c, Blocks.STONE), cobble = count(c, Blocks.COBBLESTONE);
            return stone == 2 && cobble == 2 ? null : "items left: stone " + stone + " (wanted 2), cobblestone " + cobble + " (wanted 2)";
        };
        register(r, p1);

        Scenario p2 = new Scenario("printer/place/only-missing-blocks");
        for (int x = 0; x < 4; x++) p2.put(x, 0, 0, STONE);
        p2.preplaced.put(new BlockPos(0, 0, 0), STONE);
        p2.preplaced.put(new BlockPos(1, 0, 0), STONE);
        p2.check = (c, s) -> {
            String problem = Scenario.allBuilt(c, s);
            if (problem != null) return problem;
            for (int x = 0; x < 2; x++) if (placedIndex(s, new BlockPos(x, 0, 0)) >= 0) return "the printer placed over the pre-placed block at " + x;
            return Printer.PLACED.size() == 2 ? null : "the printer placed " + Printer.PLACED.size() + " blocks, wanted 2";
        };
        register(r, p2);

        Scenario p3 = new Scenario("printer/place/nothing-outside-the-placement");
        p3.put(1, 0, 1, STONE).put(2, 0, 1, STONE).put(1, 0, 2, STONE);
        p3.stable = 30;
        p3.check = (c, s) -> {
            String problem = Scenario.allBuilt(c, s);
            if (problem != null) return problem;
            for (int x = 0; x <= 3; x++) {
                for (int z = 0; z <= 3; z++) {
                    BlockPos rel = new BlockPos(x, 0, z);
                    if (!s.build.containsKey(rel) && !Scenario.isAir(c, s, rel)) return "a block was placed outside the placement at " + rel;
                }
            }
            return null;
        };
        register(r, p3);

        Scenario p4 = new Scenario("printer/place/range-limit");
        p4.range = 2;
        p4.put(1, 0, 2, STONE).put(1, 0, 0, STONE);
        p4.stable = 40;
        p4.check = (c, s) -> {
            if (Scenario.isAir(c, s, new BlockPos(1, 0, 2))) return "the block inside the range is not placed yet";
            return Scenario.isAir(c, s, new BlockPos(1, 0, 0)) ? null : "the printer placed a block outside its range";
        };
        register(r, p4);

        Scenario p5 = new Scenario("printer/place/survival-needs-items");
        p5.give = false;
        p5.put(0, 0, 0, STONE).put(1, 0, 0, STONE);
        boolean[] early = {false};
        p5.at.put(30, c -> p5.command(c.mc, "give @s minecraft:stone 5"));
        p5.check = (c, s) -> {
            if (!Printer.PLACED.isEmpty() && s.sinceReady() < 28) early[0] = true;
            if (early[0]) return "the printer placed a block without an item in the inventory";
            return s.sinceReady() < 30 ? "waiting for the item" : Scenario.allBuilt(c, s);
        };
        register(r, p5);

        Scenario p6 = new Scenario("printer/place/wrong-block-left-alone");
        p6.put(0, 0, 0, STONE).put(1, 0, 0, STONE).put(2, 0, 0, STONE);
        p6.preplaced.put(new BlockPos(1, 0, 0), Blocks.DIRT.getDefaultState());
        p6.stable = 30;
        p6.check = (c, s) -> {
            boolean ends = !Scenario.isAir(c, s, new BlockPos(0, 0, 0)) && !Scenario.isAir(c, s, new BlockPos(2, 0, 0));
            if (!ends) return "the missing blocks next to the wrong one are not placed yet";
            return c.mc.world.getBlockState(s.world(new BlockPos(1, 0, 0))).getBlock() == Blocks.DIRT ? null : "the wrong block was changed";
        };
        register(r, p6);

        // printer/rotation/: facing and orientation right on a server without a mod
        rotation(r, "piston-east", facing(Blocks.PISTON, EnumFacing.EAST), 0, 0, 0);
        rotation(r, "sticky-piston-north", facing(Blocks.STICKY_PISTON, EnumFacing.NORTH), 0, 0, 0);
        rotation(r, "observer-west", facing(Blocks.OBSERVER, EnumFacing.WEST), 0, 0, 0);
        rotation(r, "dispenser-south", facing(Blocks.DISPENSER, EnumFacing.SOUTH), 0, 0, 0);
        rotation(r, "dropper-east", facing(Blocks.DROPPER, EnumFacing.EAST), 0, 0, 0);
        rotation(r, "stairs-top-half", Blocks.STONE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.WEST)
                .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.TOP), 0, 0, 0, 1, 0, 0, STONE);
        rotation(r, "slab-top-half", Blocks.STONE_SLAB.getDefaultState().withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.TOP)
                .withProperty(BlockStoneSlab.VARIANT, BlockStoneSlab.EnumType.STONE), 0, 0, 0, 1, 0, 0, STONE);
        rotation(r, "log-axis-x", Blocks.LOG.getDefaultState().withProperty(BlockOldLog.VARIANT, net.minecraft.block.BlockPlanks.EnumType.OAK)
                .withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.X), 0, 0, 0, 1, 0, 0, STONE);
        rotation(r, "repeater-facing-and-delay", Blocks.UNPOWERED_REPEATER.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.WEST)
                .withProperty(BlockRedstoneRepeater.DELAY, 4), 0, 0, 0);
        rotation(r, "comparator-subtract-mode", Blocks.UNPOWERED_COMPARATOR.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.SOUTH)
                .withProperty(BlockRedstoneComparator.MODE, BlockRedstoneComparator.Mode.SUBTRACT), 0, 0, 0);
        rotation(r, "lever-on-wall", Blocks.LEVER.getDefaultState().withProperty(BlockLever.FACING, BlockLever.EnumOrientation.EAST), 1, 0, 0, 0, 0, 0, STONE);
        rotation(r, "fence-gate-opened", Blocks.OAK_FENCE_GATE.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.SOUTH)
                .withProperty(BlockFenceGate.OPEN, true), 0, 0, 0);
        rotation(r, "lever-switched-on", Blocks.LEVER.getDefaultState().withProperty(BlockLever.FACING, BlockLever.EnumOrientation.EAST)
                .withProperty(BlockLever.POWERED, true), 1, 0, 0, 0, 0, 0, STONE);
        rotation(r, "button-on-wall", facing(Blocks.STONE_BUTTON, EnumFacing.EAST), 1, 0, 0, 0, 0, 0, STONE);
        rotation(r, "hopper-east", facing(Blocks.HOPPER, EnumFacing.EAST), 0, 0, 0, 1, 0, 0, STONE);
        rotation(r, "chest-west", Blocks.CHEST.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.WEST), 0, 0, 0);
        rotation(r, "furnace-north", Blocks.FURNACE.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.NORTH), 0, 0, 0);
        rotation(r, "piston-up-needs-fake-position", facing(Blocks.PISTON, EnumFacing.UP), 0, 0, 0);
        // a piston facing down: hangs under a stone block, and the player's fake position is below it
        Scenario down = new Scenario("printer/rotation/observer-down-hangs-under-stone");
        down.range = 7;
        for (int y = 0; y <= 6; y++) down.put(1, y, 0, STONE);
        down.put(0, 6, 0, STONE);
        down.put(0, 5, 0, facing(Blocks.OBSERVER, EnumFacing.DOWN));
        register(r, down);

        // printer/order/: dependencies first, components that fire last
        Scenario o1 = new Scenario("printer/order/support-before-attached-block");
        o1.put(0, 0, 0, Blocks.LEVER.getDefaultState().withProperty(BlockLever.FACING, BlockLever.EnumOrientation.WEST)).put(1, 0, 0, STONE);
        o1.check = (c, s) -> {
            String problem = Scenario.allBuilt(c, s);
            if (problem != null) return problem;
            return placedIndex(s, new BlockPos(1, 0, 0)) < placedIndex(s, new BlockPos(0, 0, 0)) ? null : "the lever was placed before its stone";
        };
        register(r, o1);

        Scenario o2 = new Scenario("printer/order/components-that-fire-come-last");
        o2.put(0, 0, 0, facing(Blocks.OBSERVER, EnumFacing.EAST)).put(0, 0, 1, facing(Blocks.PISTON, EnumFacing.NORTH))
            .put(1, 0, 0, Blocks.UNPOWERED_REPEATER.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.SOUTH))
            .put(2, 0, 0, STONE).put(3, 0, 0, STONE).put(2, 0, 1, STONE).put(3, 0, 1, STONE);
        o2.check = (c, s) -> {
            String problem = Scenario.allBuilt(c, s);
            if (problem != null) return problem;
            int lastStone = 0, repeater = placedIndex(s, new BlockPos(1, 0, 0));
            for (BlockPos rel : s.build.keySet()) if (s.build.get(rel) == STONE) lastStone = Math.max(lastStone, placedIndex(s, rel));
            int firstFiring = Math.min(placedIndex(s, new BlockPos(0, 0, 0)), placedIndex(s, new BlockPos(0, 0, 1)));
            if (lastStone > repeater) return "a stone block (" + lastStone + ") came after the repeater (" + repeater + ")";
            return repeater < firstFiring ? null : "the repeater (" + repeater + ") came after an observer or piston (" + firstFiring + ")";
        };
        register(r, o2);

        Scenario o3 = new Scenario("printer/order/missing-item-holds-back-firing-components");
        o3.give = false;
        o3.put(1, 0, 0, STONE).put(0, 0, 0, facing(Blocks.OBSERVER, EnumFacing.EAST));
        o3.extraCommands.add("give @s minecraft:observer 3");
        boolean[] firedEarly = {false};
        o3.at.put(25, c -> o3.command(c.mc, "give @s minecraft:stone 3"));
        o3.check = (c, s) -> {
            if (s.sinceReady() < 24 && !Scenario.isAir(c, s, new BlockPos(0, 0, 0))) firedEarly[0] = true;
            if (firedEarly[0]) return "the observer was placed while the stone it waits for had no item";
            if (s.sinceReady() < 26) return "waiting for the stone item";
            String problem = Scenario.allBuilt(c, s);
            if (problem != null) return problem;
            return placedIndex(s, new BlockPos(1, 0, 0)) < placedIndex(s, new BlockPos(0, 0, 0)) ? null : "the observer came before the stone";
        };
        register(r, o3);

        // printer/mining/: hand mining and the printer side by side
        Scenario m1 = mining("printer/mining/printer-pauses-while-mining", 5);
        register(r, m1);
        Scenario m2 = mining("printer/mining/hand-broken-block-not-put-back", 40);
        register(r, m2);

        // printer/config/: the tab, the hotkey, the saved and limited values
        r.add("printer/config/tab-in-litematica-config-screen", c -> {
            c.check(Registry.CONFIG_TAB.getExtraConfigScreenTabsFor(Reference.MOD_INFO).contains(PrinterInit.TAB), "the Litematica Upgraded tab is not among Litematica's extra config tabs");
            BaseConfigScreen screen = ConfigScreen.createOnTab(PrinterInit.TAB);
            c.check(screen != null, "no config screen");
            c.check(!I18n.format("litematica_upgraded.config.tab.printer").startsWith("litematica_upgraded."), "the tab name has no translation");
            for (ConfigInfo option : PrinterConfigs.OPTIONS) {
                String name = "litematica_upgraded.config.name." + option.getName().toLowerCase();
                c.check(I18n.hasKey(name) && I18n.hasKey(name.replace(".name.", ".comment.")), "no name or tooltip translation for " + option.getName());
            }
            c.check(PrinterConfigs.OPTIONS.contains(PrinterConfigs.PRINTER_ENABLED) && PrinterInit.TAB.getConfigs().size() == PrinterConfigs.OPTIONS.size(), "the tab does not list every printer setting");
        });
        r.add("printer/config/hotkey-toggles-printer", c -> {
            c.check(new PrinterInit.Provider().getAllHotkeys().contains(PrinterConfigs.PRINTER_ENABLED), "the printer hotkey is not registered");
            c.check(!PrinterConfigs.PRINTER_ENABLED.getKeyBind().getDefaultKeysDisplayString().isEmpty(), "the printer toggle has no default key");
            PrinterConfigs.PRINTER_ENABLED.setBooleanValue(false);
            PrinterConfigs.PRINTER_ENABLED.getToggleAction().execute(ActionContext.COMMON);
            c.check(PrinterConfigs.PRINTER_ENABLED.getBooleanValue() && Printer.INSTANCE.isEnabled(), "the hotkey action did not turn the printer on");
            PrinterConfigs.PRINTER_ENABLED.getToggleAction().execute(ActionContext.COMMON);
            c.check(!PrinterConfigs.PRINTER_ENABLED.getBooleanValue(), "the hotkey action did not turn the printer off");
        });
        r.add("printer/config/values-limited-and-saved", c -> {
            PrinterConfigs.RANGE.setIntegerValue(99);
            c.check(PrinterConfigs.RANGE.getIntegerValue() == 8, "range 99 became " + PrinterConfigs.RANGE.getIntegerValue() + ", not the maximum 8");
            PrinterConfigs.RANGE.setIntegerValue(-3);
            c.check(PrinterConfigs.RANGE.getIntegerValue() == 1, "range -3 became " + PrinterConfigs.RANGE.getIntegerValue() + ", not the minimum 1");
            PrinterConfigs.BLOCKS_PER_TICK.setIntegerValue(500);
            c.check(PrinterConfigs.BLOCKS_PER_TICK.getIntegerValue() == 16, "blocks per tick 500 was not limited to 16");
            PrinterConfigs.BLOCKS_PER_TICK.setIntegerValue(3);
            PrinterConfigs.RANGE.setIntegerValue(7);
            Registry.CONFIG_MANAGER.getConfigHandler(PrinterConfigs.MOD_INFO).saveToFile();
            String saved = null;
            File[] files = new File("config").listFiles();
            if (files != null) {
                for (File file : files) {
                    String text = file.isFile() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : "";
                    if (text.contains("printerRange")) saved = text;
                }
            }
            c.check(saved != null, "no saved config file holds the printer settings");
            c.check(saved.replaceAll("\\s", "").contains("\"printerRange\":7"), "the saved file does not hold the range 7");
            PrinterConfigs.RANGE.setIntegerValue(6);
        });

        // easyplace/: Litematica's Easy Place without a server mod
        easyPlace(r, "piston-facing-up", facing(Blocks.PISTON, EnumFacing.UP));
        easyPlace(r, "stairs-facing-and-half", Blocks.STONE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.SOUTH)
                .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.BOTTOM));
        easyPlace(r, "observer-facing", facing(Blocks.OBSERVER, EnumFacing.WEST));
        easyPlace(r, "chest-facing", Blocks.CHEST.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.EAST));
        // easyplace/build-limit/: a block in the top layer of the world (y 255), from a platform just below it
        buildLimit(r, "fake-rotation", STONE, true, false, true);
        buildLimit(r, "plain", STONE, false, false, true);
        buildLimit(r, "plain-click-adjacent", STONE, false, true, true);
        // slabs: Easy Place clicks the spot's own top face for a bottom half, which the server refuses up there
        IBlockState bottomSlab = Blocks.STONE_SLAB.getDefaultState().withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.BOTTOM)
            .withProperty(BlockStoneSlab.VARIANT, BlockStoneSlab.EnumType.STONE);
        buildLimit(r, "bottom-slab", bottomSlab, true, false, true);
        buildLimit(r, "bottom-slab-over-nothing", bottomSlab, true, false, false);
        buildLimit(r, "bottom-slab-click-adjacent", bottomSlab, false, true, true);
        buildLimit(r, "top-slab", bottomSlab.withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.TOP), true, false, true);
        buildLimit(r, "double-slab", Blocks.DOUBLE_STONE_SLAB.getDefaultState().withProperty(BlockStoneSlab.VARIANT, BlockStoneSlab.EnumType.STONE), true, false, true);
        // easyplace/state/: what only a click on the placed block sets (open, switched on), in the same Easy Place click
        IBlockState trapdoorOpen = Blocks.TRAPDOOR.getDefaultState().withProperty(BlockTrapDoor.FACING, EnumFacing.WEST)
                .withProperty(BlockTrapDoor.HALF, BlockTrapDoor.DoorHalf.TOP).withProperty(BlockTrapDoor.OPEN, true);
        easyPlace(r, "state/trapdoor-open-top-half", trapdoorOpen, java.util.Collections.singletonMap(new BlockPos(1, 0, 0), STONE));
        easyPlace(r, "state/fence-gate-open", Blocks.OAK_FENCE_GATE.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.NORTH)
                .withProperty(BlockFenceGate.OPEN, true));
        easyPlace(r, "state/lever-on", Blocks.LEVER.getDefaultState().withProperty(BlockLever.FACING, BlockLever.EnumOrientation.EAST)
                .withProperty(BlockLever.POWERED, true), java.util.Collections.singletonMap(new BlockPos(-1, 0, 0), STONE));
        // a trapdoor built closed: aiming Easy Place at it opens it
        java.util.Map<BlockPos, IBlockState> closed = new java.util.LinkedHashMap<>();
        closed.put(new BlockPos(1, 0, 0), STONE);
        closed.put(new BlockPos(0, 0, 0), trapdoorOpen.withProperty(BlockTrapDoor.OPEN, false));
        easyPlace(r, "state/opens-a-built-closed-trapdoor", trapdoorOpen, closed);

        // printer/break/: real survival mining of what the placement wants gone
        Scenario b1 = new Scenario("printer/break/wrong-block-mined-and-replaced");
        b1.put(0, 0, 0, STONE).put(1, 0, 0, STONE);
        b1.preplaced.put(new BlockPos(0, 0, 0), Blocks.DIRT.getDefaultState());
        b1.at.put(0, c -> PrinterConfigs.BREAK_WRONG_BLOCK.setBooleanValue(true));
        b1.check = (c, s) -> {
            String problem = Scenario.allBuilt(c, s);
            if (problem != null) return problem;
            return placedIndex(s, new BlockPos(0, 0, 0)) >= 0 ? null : "the dirt is gone but the printer did not place the stone";
        };
        register(r, b1);

        Scenario b2 = new Scenario("printer/break/extra-block-mined-outside-and-right-blocks-untouched");
        b2.put(0, 0, 0, STONE).put(2, 0, 0, STONE);
        b2.preplaced.put(new BlockPos(0, 0, 0), STONE);
        b2.preplaced.put(new BlockPos(1, 0, 0), Blocks.DIRT.getDefaultState());
        b2.preplaced.put(new BlockPos(5, 0, 0), Blocks.DIRT.getDefaultState());
        b2.stable = 40;
        boolean[] rightBlockBroken = {false};
        b2.at.put(0, c -> PrinterConfigs.BREAK_EXTRA_BLOCK.setBooleanValue(true));
        b2.check = (c, s) -> {
            if (Scenario.isAir(c, s, new BlockPos(0, 0, 0))) rightBlockBroken[0] = true;
            if (rightBlockBroken[0]) return "the printer mined a block that was right";
            String problem = Scenario.allBuilt(c, s);
            if (problem != null) return problem;
            if (!Scenario.isAir(c, s, new BlockPos(1, 0, 0))) return "the extra block inside the placement is still there";
            return c.mc.world.getBlockState(s.world(new BlockPos(5, 0, 0))).getBlock() == Blocks.DIRT ? null : "the printer mined a block outside the placement";
        };
        register(r, b2);

        Scenario b3 = new Scenario("printer/break/wrong-state-mined-and-replaced");
        b3.put(0, 0, 0, Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.WEST));
        b3.preplaced.put(new BlockPos(0, 0, 0), Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.EAST));
        b3.at.put(0, c -> PrinterConfigs.BREAK_WRONG_STATE.setBooleanValue(true));
        register(r, b3);

        // printer/tool/: the best tool of the inventory, a hoe for farmland
        Scenario t1 = new Scenario("printer/tool/best-tool-picked-from-the-inventory");
        t1.put(0, 0, 0, Blocks.PLANKS.getDefaultState());
        t1.preplaced.put(new BlockPos(0, 0, 0), STONE);
        t1.extraCommands.add("give @s minecraft:wooden_pickaxe 1");
        t1.extraCommands.add("give @s minecraft:iron_pickaxe 1");
        t1.at.put(0, c -> PrinterConfigs.BREAK_WRONG_BLOCK.setBooleanValue(true));
        t1.check = (c, s) -> {
            String problem = Scenario.allBuilt(c, s);
            if (problem != null) return problem;
            if (damage(c, Items.WOODEN_PICKAXE) != 0) return "the wooden pickaxe was used (damage " + damage(c, Items.WOODEN_PICKAXE) + ")";
            return damage(c, Items.IRON_PICKAXE) > 0 ? null : "the iron pickaxe was not used";
        };
        register(r, t1);

        Scenario t2 = new Scenario("printer/tool/hoe-tills-farmland");
        t2.put(0, 0, 0, Blocks.FARMLAND.getDefaultState());
        t2.preplaced.put(new BlockPos(0, 0, 0), Blocks.DIRT.getDefaultState());
        t2.extraCommands.add("give @s minecraft:iron_hoe 1");
        t2.stable = 5;
        // the dirt must not be mined although it is the wrong block: a hoe fixes it
        t2.at.put(0, c -> PrinterConfigs.BREAK_WRONG_BLOCK.setBooleanValue(true));
        t2.check = (c, s) -> {
            String problem = Scenario.allBuilt(c, s);
            if (problem != null) return problem;
            return damage(c, Items.IRON_HOE) > 0 ? null : "the farmland did not come from the hoe";
        };
        register(r, t2);

        // printer/scaffold/: a temporary block under a fence gate over air
        Scenario sc1 = new Scenario("printer/scaffold/fence-gate-over-air");
        sc1.put(0, 1, 0, Blocks.OAK_FENCE_GATE.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.NORTH));
        sc1.extraCommands.add("give @s minecraft:dirt 3");
        boolean[] scaffoldSeen = {false};
        sc1.check = (c, s) -> {
            if (!Scenario.isAir(c, s, new BlockPos(0, 0, 0))) scaffoldSeen[0] = true;
            String problem = Scenario.allBuilt(c, s);
            if (problem != null) return problem;
            if (!scaffoldSeen[0]) return "the gate stands but no scaffold was ever placed under it";
            return Scenario.isAir(c, s, new BlockPos(0, 0, 0)) ? null : "the scaffold block is still under the gate";
        };
        register(r, sc1);

        // printer/fill/: item filters
        Scenario f1 = new Scenario("printer/fill/hopper-filter-filled-into-the-right-slots");
        f1.put(0, 0, 0, facing(Blocks.HOPPER, EnumFacing.DOWN));
        f1.tiles.put(new BlockPos(0, 0, 0), containerTile("minecraft:hopper", item(0, "minecraft:stone", 1), item(2, "minecraft:dirt", 1), item(4, "minecraft:cobblestone", 2)));
        f1.extraCommands.add("give @s minecraft:stone 2");
        f1.extraCommands.add("give @s minecraft:dirt 2");
        f1.extraCommands.add("give @s minecraft:cobblestone 4");
        f1.at.put(0, c -> PrinterConfigs.FILL_CONTAINERS.setBooleanValue(true));
        f1.stable = 3;
        f1.check = containerCheck(new BlockPos(0, 0, 0), new ItemStack(Blocks.STONE), ItemStack.EMPTY, new ItemStack(Blocks.DIRT), ItemStack.EMPTY, new ItemStack(Blocks.COBBLESTONE, 2));
        register(r, f1);

        Scenario f2 = new Scenario("printer/fill/slots-with-other-items-left-alone");
        f2.put(0, 0, 0, facing(Blocks.DROPPER, EnumFacing.EAST));
        f2.preplaced.put(new BlockPos(0, 0, 0), facing(Blocks.DROPPER, EnumFacing.EAST));
        f2.tiles.put(new BlockPos(0, 0, 0), containerTile("minecraft:dropper", item(0, "minecraft:stone", 1), item(1, "minecraft:dirt", 1), item(2, "minecraft:cobblestone", 1)));
        f2.extraCommands.add("give @s minecraft:stone 2");
        f2.extraCommands.add("give @s minecraft:dirt 2");
        f2.extraCommands.add("give @s minecraft:cobblestone 2");
        f2.autoEnable = false;
        // the dropper already holds sand in the slot the schematic wants dirt in; the printer starts once that is there
        f2.at.put(0, c -> {
            BlockPos at = f2.world(new BlockPos(0, 0, 0));
            f2.command(c.mc, "replaceitem block " + at.getX() + " " + at.getY() + " " + at.getZ() + " slot.container.1 minecraft:sand 3");
        });
        f2.at.put(10, c -> {
            PrinterConfigs.FILL_CONTAINERS.setBooleanValue(true);
            PrinterConfigs.PRINTER_ENABLED.setBooleanValue(true);
        });
        f2.stable = 3;
        f2.check = containerCheck(new BlockPos(0, 0, 0), new ItemStack(Blocks.STONE), new ItemStack(Blocks.SAND, 3), new ItemStack(Blocks.COBBLESTONE));
        register(r, f2);

        // printer/fill/: chests (also a double trapped chest), furnace, brewing stand, shulker box
        IBlockState chestNorth = Blocks.CHEST.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.NORTH);
        fill(r, "chest-filled-into-the-right-slots", new BlockPos[] {new BlockPos(0, 0, 0)}, new IBlockState[] {chestNorth},
            new NBTTagCompound[] {containerTile("minecraft:chest", item(0, "minecraft:dirt", 3), item(13, "minecraft:cobblestone", 2), item(26, "minecraft:stone", 1))},
            slots(27, 0, new ItemStack(Blocks.DIRT, 3), 13, new ItemStack(Blocks.COBBLESTONE, 2), 26, new ItemStack(Blocks.STONE)),
            "give @s minecraft:dirt 3", "give @s minecraft:cobblestone 2", "give @s minecraft:stone 1");
        IBlockState trapped = Blocks.TRAPPED_CHEST.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.NORTH);
        fill(r, "double-trapped-chest-both-halves-filled", new BlockPos[] {new BlockPos(0, 0, 0), new BlockPos(1, 0, 0)}, new IBlockState[] {trapped, trapped},
            new NBTTagCompound[] {containerTile("minecraft:chest", item(0, "minecraft:dirt", 2), item(26, "minecraft:cobblestone", 1)),
                containerTile("minecraft:chest", item(0, "minecraft:stone", 1), item(5, "minecraft:sand", 2))},
            slots(54, 0, new ItemStack(Blocks.DIRT, 2), 26, new ItemStack(Blocks.COBBLESTONE), 27, new ItemStack(Blocks.STONE), 32, new ItemStack(Blocks.SAND, 2)),
            "give @s minecraft:dirt 2", "give @s minecraft:cobblestone 1", "give @s minecraft:stone 1", "give @s minecraft:sand 2");
        // dirt does not smelt, so the coal is not burnt; the output slot (an iron ingot in the schematic) stays empty
        fill(r, "furnace-input-and-fuel-filled-output-left-empty", new BlockPos[] {new BlockPos(0, 0, 0)},
            new IBlockState[] {Blocks.FURNACE.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.NORTH)},
            new NBTTagCompound[] {containerTile("minecraft:furnace", item(0, "minecraft:dirt", 3), item(1, "minecraft:coal", 2), item(2, "minecraft:iron_ingot", 1))},
            slots(3, 0, new ItemStack(Blocks.DIRT, 3), 1, new ItemStack(Items.COAL, 2)),
            "give @s minecraft:dirt 3", "give @s minecraft:coal 2");
        // no blaze powder: the stand would burn it at once
        fill(r, "brewing-stand-bottles-and-ingredient-filled", new BlockPos[] {new BlockPos(0, 0, 0)}, new IBlockState[] {Blocks.BREWING_STAND.getDefaultState()},
            new NBTTagCompound[] {containerTile("minecraft:brewing_stand", item(0, "minecraft:glass_bottle", 1), item(2, "minecraft:glass_bottle", 1), item(3, "minecraft:nether_wart", 2))},
            slots(5, 0, new ItemStack(Items.GLASS_BOTTLE), 2, new ItemStack(Items.GLASS_BOTTLE), 3, new ItemStack(Items.NETHER_WART, 2)),
            "give @s minecraft:glass_bottle 2", "give @s minecraft:nether_wart 2");
        fill(r, "shulker-box-placed-empty-then-filled", new BlockPos[] {new BlockPos(0, 0, 0)}, new IBlockState[] {Blocks.PURPLE_SHULKER_BOX.getDefaultState()},
            new NBTTagCompound[] {containerTile("minecraft:shulker_box", item(0, "minecraft:dirt", 5), item(13, "minecraft:cobblestone", 2))},
            slots(27, 0, new ItemStack(Blocks.DIRT, 5), 13, new ItemStack(Blocks.COBBLESTONE, 2)),
            "give @s minecraft:dirt 5", "give @s minecraft:cobblestone 2");

        // printer/fluid/: sources from buckets, water from ice
        fluid(r, "water-source-placed-with-bucket", Blocks.WATER.getDefaultState(), "give @s minecraft:water_bucket 1");
        fluid(r, "lava-source-placed-with-bucket", Blocks.LAVA.getDefaultState(), "give @s minecraft:lava_bucket 1");
        fluid(r, "water-source-made-from-ice", Blocks.WATER.getDefaultState(), "give @s minecraft:ice 3").give = false;

        // printer/snow/: layers built up
        Scenario sn1 = new Scenario("printer/snow/layers-built-up-to-the-schematic-count");
        sn1.put(0, 0, 0, Blocks.SNOW_LAYER.getDefaultState().withProperty(BlockSnow.LAYERS, 3));
        sn1.stable = 5;
        register(r, sn1);

        // printer/sign/: the schematic sign's text in the sign editor
        Scenario si1 = new Scenario("printer/sign/sign-editor-gets-the-schematic-text");
        si1.put(0, 0, 0, STONE).put(1, 0, 0, Blocks.WALL_SIGN.getDefaultState().withProperty(BlockWallSign.FACING, EnumFacing.EAST));
        NBTTagCompound signTile = new NBTTagCompound();
        signTile.setString("id", "minecraft:sign");
        String[] lines = {"Hello", "from the", "schematic", "sign"};
        for (int i = 0; i < 4; i++) signTile.setString("Text" + (i + 1), "{\"text\":\"" + lines[i] + "\"}");
        si1.tiles.put(new BlockPos(1, 0, 0), signTile);
        boolean[] pasted = {false};
        String[] pasteProblem = {null};
        si1.check = (c, s) -> {
            if (c.mc.currentScreen instanceof GuiEditSign) {
                TileEntitySign sign = SignTextPaste.signOf((GuiEditSign) c.mc.currentScreen);
                if (sign == null) pasteProblem[0] = "the sign editor has no sign";
                for (int i = 0; i < 4 && sign != null; i++) {
                    String text = sign.signText[i].getUnformattedText();
                    if (!text.equals(lines[i])) pasteProblem[0] = "line " + (i + 1) + " of the editor is '" + text + "', wanted '" + lines[i] + "'";
                }
                pasted[0] = true;
                c.mc.displayGuiScreen(null); // closing the editor sends its text to the server
            }
            String problem = Scenario.allBuilt(c, s);
            if (problem != null) return problem;
            if (!pasted[0]) return "the sign editor never opened";
            if (pasteProblem[0] != null) return pasteProblem[0];
            net.minecraft.tileentity.TileEntity tile = c.mc.world.getTileEntity(s.world(new BlockPos(1, 0, 0)));
            if (!(tile instanceof TileEntitySign)) return "the sign has no tile entity";
            for (int i = 0; i < 4; i++) {
                String text = ((TileEntitySign) tile).signText[i].getUnformattedText();
                if (!text.equals(lines[i])) return "the placed sign's line " + (i + 1) + " is '" + text + "', wanted '" + lines[i] + "'";
            }
            return null;
        };
        register(r, si1);

        // printer/destroy/: hold attack on a wrong block, only wanted-gone blocks are mined
        Scenario d1 = new Scenario("printer/destroy/hold-attack-mines-only-wrong-blocks");
        d1.put(0, 0, 0, STONE).put(1, 0, 0, STONE).put(2, 0, 0, STONE);
        d1.preplaced.put(new BlockPos(0, 0, 0), Blocks.DIRT.getDefaultState());
        d1.preplaced.put(new BlockPos(1, 0, 0), STONE);
        d1.preplaced.put(new BlockPos(2, 0, 0), Blocks.DIRT.getDefaultState());
        d1.autoEnable = false;
        d1.stable = 15;
        boolean[] pressed = {false}, rightBlockMined = {false};
        d1.at.put(0, c -> {
            PrinterConfigs.DESTROY_ASSIST.setBooleanValue(true);
            DestroyAssist.held = () -> true;
        });
        d1.check = (c, s) -> {
            BlockPos first = s.world(new BlockPos(0, 0, 0));
            if (Scenario.isAir(c, s, new BlockPos(1, 0, 0))) rightBlockMined[0] = true;
            if (rightBlockMined[0]) return "the destroy assist mined a block that was right";
            if (!pressed[0]) {
                // look at the first wrong block, then press attack
                net.minecraft.util.math.Vec3d eye = c.mc.player.getPositionEyes(1f);
                double dx = first.getX() + 0.5 - eye.x, dy = first.getY() + 0.5 - eye.y, dz = first.getZ() + 0.5 - eye.z;
                c.mc.player.rotationYaw = c.mc.player.prevRotationYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                c.mc.player.rotationPitch = c.mc.player.prevRotationPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                net.minecraft.util.math.RayTraceResult hit = c.mc.objectMouseOver;
                if (hit != null && hit.typeOfHit == net.minecraft.util.math.RayTraceResult.Type.BLOCK && hit.getBlockPos().equals(first) && s.sinceReady() > 3) {
                    pressed[0] = DestroyAssist.press(c.mc);
                }
                return "waiting to aim at the first wrong block";
            }
            if (!Scenario.isAir(c, s, new BlockPos(0, 0, 0))) return "the first wrong block is not mined yet";
            if (!Scenario.isAir(c, s, new BlockPos(2, 0, 0))) return "the assist did not go on to the second wrong block";
            return null;
        };
        register(r, d1);

        // printer/area/: the layer range limits the printer
        Scenario a1 = new Scenario("printer/area/single-layer-limits-the-printer");
        a1.put(0, 0, 0, STONE).put(1, 0, 0, STONE).put(0, 1, 0, STONE).put(1, 1, 0, STONE);
        a1.stable = 40;
        a1.at.put(0, c -> {
            LayerRange layers = DataManager.getRenderLayerRange();
            layers.setLayerMode(LayerMode.SINGLE_LAYER, false);
            layers.setLayerSingle(a1.origin.getY());
        });
        a1.check = (c, s) -> {
            if (s.sinceReady() < 1) return "waiting";
            if (Scenario.isAir(c, s, new BlockPos(0, 0, 0)) || Scenario.isAir(c, s, new BlockPos(1, 0, 0))) return "the layer inside the range is not built yet";
            return Scenario.isAir(c, s, new BlockPos(0, 1, 0)) && Scenario.isAir(c, s, new BlockPos(1, 1, 0)) ? null : "the printer built outside the layer";
        };
        register(r, a1);

        Scenario a2 = new Scenario("printer/area/layer-on-the-x-axis-limits-the-printer");
        a2.put(0, 0, 0, STONE).put(1, 0, 0, STONE).put(1, 0, 1, STONE).put(2, 0, 1, STONE);
        a2.stable = 40;
        a2.at.put(0, c -> {
            LayerRange layers = DataManager.getRenderLayerRange();
            layers.setLayerMode(LayerMode.SINGLE_LAYER, false);
            layers.setAxis(Direction.Axis.X);
            layers.setLayerSingle(a2.origin.getX() + 1);
        });
        a2.check = (c, s) -> {
            if (s.sinceReady() < 1) return "waiting";
            if (Scenario.isAir(c, s, new BlockPos(1, 0, 0)) || Scenario.isAir(c, s, new BlockPos(1, 0, 1))) return "the layer inside the range is not built yet";
            return Scenario.isAir(c, s, new BlockPos(0, 0, 0)) && Scenario.isAir(c, s, new BlockPos(2, 0, 1)) ? null : "the printer built outside the layer";
        };
        register(r, a2);

        // printer/stop/: dying turns the printer off
        Scenario st1 = new Scenario("printer/stop/printer-stops-after-the-player-dies");
        for (int x = 0; x < 6; x++) for (int z = 0; z < 5; z++) st1.put(x, 0, z, STONE);
        st1.range = 6;
        st1.perTick = 1;
        st1.stable = 5;
        int[] deadTicks = {0};
        boolean[] respawned = {false};
        st1.at.put(2, c -> st1.command(c.mc, "kill @s"));
        st1.check = (c, s) -> {
            if (!respawned[0]) {
                if (c.mc.player.getHealth() > 0) return "waiting for the player to die";
                if (++deadTicks[0] < 4) return "dead, waiting for the printer to notice";
                if (PrinterConfigs.PRINTER_ENABLED.getBooleanValue()) return "the player is dead but the printer is still on";
                if (Printer.PLACED.size() >= 30) return "the printer had built everything before the player died";
                respawned[0] = true;
                c.mc.player.respawnPlayer();
                return "respawning";
            }
            if (c.mc.player.getHealth() <= 0) return "waiting for the respawn";
            return PrinterConfigs.PRINTER_ENABLED.getBooleanValue() ? "the printer turned itself on again" : null;
        };
        register(r, st1);

        // easyplace/plain/: Litematica's own Easy Place, fake rotation off, behaves as upstream
        Scenario plainStone = plainEasyPlace("places-block-where-aimed", 0, 0, 0);
        plainStone.put(0, 0, 0, STONE);
        register(r, plainStone);
        // a button in the air next to one stone block: the only block to click is the wall, so its facing is the clicked face
        Scenario plainButton = plainEasyPlace("places-wall-block-on-clicked-face", 1, 1, 0);
        plainButton.preplaced.put(new BlockPos(0, 1, 0), STONE);
        plainButton.put(1, 1, 0, facing(Blocks.STONE_BUTTON, EnumFacing.EAST));
        plainButton.at.put(1, c -> com.useleess.litematicaupgraded.litematica.config.Configs.Generic.EASY_PLACE_CLICK_ADJACENT.setBooleanValue(true));
        register(r, plainButton);
        Scenario plainRefuse = plainEasyPlace("refuses-spot-without-schematic-block", 0, 0, 0);
        plainRefuse.put(3, 0, 0, STONE);
        plainRefuse.stable = 30;
        plainRefuse.check = (c, sc) -> {
            clickEasyPlace(c, sc, 0, 0, 0);
            if (sc.sinceReady() < 20) return "waiting";
            if (!Scenario.isAir(c, sc, new BlockPos(0, 0, 0))) return "Easy Place built a block where the schematic has none";
            return Scenario.isAir(c, sc, new BlockPos(3, 0, 0)) ? null : "Easy Place built a schematic block somewhere else";
        };
        register(r, plainRefuse);

        // title/: the real version, not '?'
        r.add("title/litematica-screens-show-the-version", c -> {
            String version = Loader.instance().getIndexedModList().get("litematica_upgraded").getVersion();
            c.check(version.matches("\\d+\\.\\d+\\.\\d+.*"), "the mod has no real version: " + version);
            c.check(version.equals(Reference.MOD_VERSION), "Litematica reports " + Reference.MOD_VERSION + ", the mod is " + version);
            c.check(version.equals(com.useleess.litematicaupgraded.malilib.MaLiLibReference.MOD_VERSION), "malilib reports " + com.useleess.litematicaupgraded.malilib.MaLiLibReference.MOD_VERSION);
            String title = com.useleess.litematicaupgraded.malilib.util.StringUtils.translate("litematica.title.screen.configs", Reference.MOD_VERSION);
            c.check(title.contains(version) && !title.contains("?"), "the config screen title is '" + title + "'");
        });
    }

    /** Mining by hand on the first dirt block while the printer has a stone block to place next to it. */
    private static Scenario mining(String id, int stableTicks) {
        Scenario s = new Scenario(id);
        s.put(0, 0, 0, Blocks.DIRT.getDefaultState()).put(3, 0, 0, STONE);
        s.preplaced.put(new BlockPos(0, 0, 0), Blocks.DIRT.getDefaultState());
        s.autoEnable = false;
        s.stable = stableTicks == 5 ? 10 : 40;
        int[] minedTicks = {0};
        boolean[] violated = {false}, minedOnce = {false};
        s.check = (c, sc) -> {
            BlockPos dirt = sc.world(new BlockPos(0, 0, 0));
            boolean dirtGone = c.mc.world.getBlockState(dirt).getBlock() == Blocks.AIR;
            // the player holds the attack key and looks at the dirt, so the game itself mines it (and resets the mining when the key is not held)
            net.minecraft.client.settings.KeyBinding.setKeyBindState(c.mc.gameSettings.keyBindAttack.getKeyCode(), !dirtGone);
            if (!dirtGone) {
                net.minecraft.util.math.Vec3d eye = c.mc.player.getPositionEyes(1f);
                double dx = dirt.getX() + 0.5 - eye.x, dy = dirt.getY() + 0.5 - eye.y, dz = dirt.getZ() + 0.5 - eye.z;
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)), pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                c.mc.player.rotationYaw = c.mc.player.prevRotationYaw = yaw;
                c.mc.player.rotationPitch = c.mc.player.prevRotationPitch = pitch;
                if (c.mc.playerController.getIsHittingBlock()) {
                    minedTicks[0]++;
                    // the printer is turned on only once the player is really mining
                    if (!PrinterConfigs.PRINTER_ENABLED.getBooleanValue()) PrinterConfigs.PRINTER_ENABLED.setBooleanValue(true);
                }
                if (!Scenario.isAir(c, sc, new BlockPos(3, 0, 0))) violated[0] = true;
            }
            if (violated[0]) return "the printer placed a block while the player was mining by hand";
            if (dirtGone) minedOnce[0] = true;
            else if (minedOnce[0]) return "the printer put the hand-broken block back";
            if (!dirtGone) return "still mining (" + minedTicks[0] + " ticks)";
            if (minedTicks[0] < 3) return "the dirt was mined in " + minedTicks[0] + " ticks: the pause was not tested";
            if (Scenario.isAir(c, sc, new BlockPos(3, 0, 0))) return "the printer did not carry on after the mining";
            return null;
        };
        return s;
    }

    /** Easy Place places the target block (and what it needs next to it) the way the schematic has it. */
    private static Scenario easyPlace(Runner.Registry r, String name, IBlockState target) {
        return easyPlace(r, name, target, java.util.Collections.emptyMap());
    }

    /** As above, with blocks already in the world (relative position -> state) before Easy Place clicks. */
    private static Scenario easyPlace(Runner.Registry r, String name, IBlockState target, java.util.Map<BlockPos, IBlockState> preplaced) {
        Scenario s = new Scenario("easyplace/" + name);
        s.autoEnable = false;
        s.preplaced.putAll(preplaced);
        s.put(0, 0, 0, target);
        s.at.put(0, c -> PrinterConfigs.EASY_PLACE_FAKE_ROTATION.setBooleanValue(true));
        s.check = (c, sc) -> {
            String problem = Scenario.allBuilt(c, sc);
            if (problem == null) {
                EASY_TARGET.target = null;
                return null;
            }
            if (sc.sinceReady() % 4 == 0 && sc.sinceReady() >= 0) {
                EASY_TARGET.target = new com.useleess.litematicaupgraded.malilib.util.position.BlockPos(sc.world(new BlockPos(0, 0, 0)).getX(),
                        sc.world(new BlockPos(0, 0, 0)).getY(), sc.world(new BlockPos(0, 0, 0)).getZ());
                EasyPlaceUtils.handleEasyPlaceWithMessage();
                EASY_TARGET.target = null;
            }
            return problem;
        };
        register(r, s);
        return s;
    }


    /** The damage of the first stack of the item in the inventory (the server updates it when the tool is used), -1 when none. */
    private static int damage(Runner.Context c, Item item) {
        for (ItemStack stack : c.mc.player.inventory.mainInventory) if (stack.getItem() == item) return stack.getItemDamage();
        return -1;
    }

    private static NBTTagCompound item(int slot, String id, int count) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByte("Slot", (byte) slot);
        tag.setString("id", id);
        tag.setByte("Count", (byte) count);
        tag.setShort("Damage", (short) 0);
        return tag;
    }

    private static NBTTagCompound containerTile(String id, NBTTagCompound... items) {
        NBTTagCompound tile = new NBTTagCompound();
        tile.setString("id", id);
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound item : items) list.appendTag(item);
        tile.setTag("Items", list);
        return tile;
    }

    /**
     * Waits for the printer to have filled the container, then opens it like a player and reads its slots: the expected
     * stacks (empty = nothing) must be in them, by item and count.
     */
    private static Scenario.Check containerCheck(BlockPos rel, ItemStack... expected) {
        int[] phase = {0, 0};
        String[] result = {null};
        return (c, s) -> {
            String problem = Scenario.allBuilt(c, s);
            if (problem != null) return problem;
            BlockPos at = s.world(rel);
            if (phase[0] == 0) {
                if (!Printer.FILLED.contains(at)) return "the container is not filled yet";
                if (c.mc.currentScreen != null) return "a screen is open";
                PrinterConfigs.PRINTER_ENABLED.setBooleanValue(false);
                c.mc.playerController.processRightClickBlock(c.mc.player, c.mc.world, at, EnumFacing.UP,
                    new net.minecraft.util.math.Vec3d(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5), net.minecraft.util.EnumHand.MAIN_HAND);
                phase[0] = 1;
                return "opening the container";
            }
            if (phase[0] == 1) {
                net.minecraft.inventory.Container container = c.mc.player.openContainer;
                if (!(c.mc.currentScreen instanceof GuiContainer) || container == c.mc.player.inventoryContainer) {
                    if (++phase[1] > 80) throw new AssertionError("the container did not open");
                    return "waiting for the container screen";
                }
                if (++phase[1] < 8) return "waiting for the contents";
                List<String> wrong = new ArrayList<>();
                for (net.minecraft.inventory.Slot slot : container.inventorySlots) {
                    if (slot.inventory == c.mc.player.inventory) continue;
                    int index = slot.getSlotIndex();
                    ItemStack want = index < expected.length ? expected[index] : ItemStack.EMPTY, have = slot.getStack();
                    boolean same = want.isEmpty() ? have.isEmpty() : !have.isEmpty() && have.isItemEqual(want) && have.getCount() == want.getCount();
                    if (!same) wrong.add("slot " + index + " holds " + have + ", wanted " + want);
                }
                c.mc.player.closeScreen();
                result[0] = wrong.isEmpty() ? null : String.join("; ", wrong) + " (last fill: " + Printer.lastFillEnd() + ")";
                phase[0] = 2;
                return "read the container";
            }
            return result[0];
        };
    }

    /** A fill test: the containers at the positions, built and filled by the printer; the first one is opened and read. */
    private static void fill(Runner.Registry r, String name, BlockPos[] at, IBlockState[] states, NBTTagCompound[] tiles, ItemStack[] expected, String... gives) {
        Scenario s = new Scenario("printer/fill/" + name);
        for (int i = 0; i < at.length; i++) {
            s.put(at[i].getX(), at[i].getY(), at[i].getZ(), states[i]);
            s.tiles.put(at[i], tiles[i]);
        }
        s.extraCommands.addAll(java.util.Arrays.asList(gives));
        s.at.put(0, c -> PrinterConfigs.FILL_CONTAINERS.setBooleanValue(true));
        s.stable = 3;
        s.check = containerCheck(at[0], expected);
        register(r, s);
    }

    /** Expected slots: the size, then slot index / stack pairs; the other slots empty. */
    private static ItemStack[] slots(int size, Object... pairs) {
        ItemStack[] slots = new ItemStack[size];
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
        for (int i = 0; i < pairs.length; i += 2) slots[(Integer) pairs[i]] = (ItemStack) pairs[i + 1];
        return slots;
    }

    /** A water or lava source in the schematic; the world must end up with a source of that fluid there. */
    private static Scenario fluid(Runner.Registry r, String name, IBlockState state, String giveCommand) {
        Scenario s = new Scenario("printer/fluid/" + name);
        s.put(1, 0, 1, state);
        s.extraCommands.add(giveCommand);
        s.stable = 5;
        s.check = (c, sc) -> {
            BlockPos at = sc.world(new BlockPos(1, 0, 1));
            IBlockState real = c.mc.world.getBlockState(at);
            if (!(real.getBlock() instanceof net.minecraft.block.BlockLiquid)) return "the spot holds " + real;
            if (real.getMaterial() != state.getMaterial()) return "the spot holds " + real + ", wanted " + state;
            return real.getValue(net.minecraft.block.BlockLiquid.LEVEL) == 0 ? null : "the fluid is flowing (" + real + "), not a source";
        };
        register(r, s);
        return s;
    }

    /** A scenario for Litematica's plain Easy Place: the fake rotation is off, the player clicks the given relative spot. */
    static Scenario plainEasyPlace(String name, int x, int y, int z) {
        Scenario s = new Scenario("easyplace/plain/" + name);
        s.autoEnable = false;
        s.at.put(0, c -> PrinterConfigs.EASY_PLACE_FAKE_ROTATION.setBooleanValue(false));
        s.check = (c, sc) -> {
            String problem = Scenario.allBuilt(c, sc);
            if (problem != null) clickEasyPlace(c, sc, x, y, z);
            return problem;
        };
        s.stable = 10;
        return s;
    }

    /**
     * Easy Place puts the target at y 255 (the top layer), on a stone at y 254 or over nothing; the player stands on a
     * platform up there, next to it (the platform's edge is the target's side neighbour).
     */
    private static void buildLimit(Runner.Registry r, String name, IBlockState target, boolean fakeRotation, boolean adjacent, boolean support) {
        Scenario s = new Scenario("easyplace/build-limit/" + name);
        s.autoEnable = false;
        // the site's ground is at y 100, so relative y 154 is world y 255
        for (int x = 0; x <= 2; x++) s.preplaced.put(new BlockPos(x, 153, 1), STONE);
        s.preplaced.put(new BlockPos(0, 153, 0), support ? STONE : Blocks.AIR.getDefaultState());
        s.preplaced.put(new BlockPos(0, 154, 0), Blocks.AIR.getDefaultState());
        s.put(0, 154, 0, target);
        s.at.put(0, c -> {
            PrinterConfigs.EASY_PLACE_FAKE_ROTATION.setBooleanValue(fakeRotation);
            com.useleess.litematicaupgraded.litematica.config.Configs.Generic.EASY_PLACE_CLICK_ADJACENT.setBooleanValue(adjacent);
            BlockPos stand = s.world(new BlockPos(1, 154, 1));
            s.command(c.mc, "tp @s " + (stand.getX() + 0.5) + " " + stand.getY() + " " + (stand.getZ() + 0.5) + " 135 45");
        });
        s.check = (c, sc) -> {
            String problem = Scenario.allBuilt(c, sc);
            if (problem != null) clickEasyPlace(c, sc, 0, 154, 0);
            return problem;
        };
        s.stable = 10;
        register(r, s);
    }

    static void clickEasyPlace(Runner.Context c, Scenario s, int x, int y, int z) {
        if (s.sinceReady() < 1 || s.sinceReady() % 4 != 0) return;
        BlockPos at = s.world(new BlockPos(x, y, z));
        EASY_TARGET.target = new com.useleess.litematicaupgraded.malilib.util.position.BlockPos(at.getX(), at.getY(), at.getZ());
        EasyPlaceUtils.handleEasyPlaceWithMessage();
        EASY_TARGET.target = null;
    }

    /** Easy Place's "overridden position": the block the test wants to place now (the real ray trace needs a mouse). */
    private static final class EasyTarget implements com.useleess.litematicaupgraded.malilib.interoperation.BlockPlacementPositionProvider {
        volatile com.useleess.litematicaupgraded.malilib.util.position.BlockPos target;

        @Override
        public com.useleess.litematicaupgraded.malilib.util.position.BlockPos getPlacementPosition() {
            return target;
        }
    }

    private static final EasyTarget EASY_TARGET = new EasyTarget();

    static {
        Registry.BLOCK_PLACEMENT_POSITION_HANDLER.registerPositionProvider(EASY_TARGET);
    }
}
