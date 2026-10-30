package com.useleess.litematicaupgraded.selftest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import net.minecraft.block.Block;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.useleess.litematicaupgraded.litematica.gui.MaterialListScreen;
import com.useleess.litematicaupgraded.litematica.gui.widget.list.entry.MaterialListEntryWidget;
import com.useleess.litematicaupgraded.litematica.materials.MaterialListBase;
import com.useleess.litematicaupgraded.litematica.materials.MaterialListEntry;
import com.useleess.litematicaupgraded.litematica.world.SchematicWorldHandler;
import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.malilib.gui.StringListSelectionScreen;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.BaseListEntryWidget;
import com.useleess.litematicaupgraded.materials.MaterialReplace;
import com.useleess.litematicaupgraded.printer.PrinterConfigs;

/**
 * material/replace/: the material list's Replace button and block picker (driven by name) swap a block in the whole
 * placement, ghost blocks (the schematic world), material counts and the printer follow, replacing back restores it.
 */
final class MaterialTests {
    private MaterialTests() {}

    private static final IBlockState WEST_STAIRS = Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.WEST);
    private static final IBlockState TOP_EAST_STAIRS = Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.EAST)
            .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.TOP);

    static void register(Runner.Registry r) {
        replaceThroughTheList(r);
        replaceAndPrint(r);
    }

    /** The schematic world's state at a test position. */
    private static IBlockState ghost(Scenario s, BlockPos rel) {
        World world = SchematicWorldHandler.getSchematicWorld();
        return world == null ? Blocks.AIR.getDefaultState() : world.getBlockState(s.world(rel));
    }

    /** Null when the schematic world has the block with the facing and half of the wanted state at every given position. */
    private static String ghostsAre(Scenario s, Block block, BlockPos... rels) {
        for (BlockPos rel : rels) {
            IBlockState want = s.build.get(rel), have = ghost(s, rel);
            boolean same = have.getBlock() == block;
            if (same && want.getPropertyKeys().contains(BlockStairs.FACING)) {
                same = have.getValue(BlockStairs.FACING) == want.getValue(BlockStairs.FACING) && have.getValue(BlockStairs.HALF) == want.getValue(BlockStairs.HALF);
            }
            if (!same) return "the ghost block at " + rel + " is " + have + ", wanted " + block.getRegistryName() + " like " + want;
        }
        return null;
    }

    /** The material list's total for an item (0 when it has no row). */
    private static long total(MaterialListBase list, Block block) {
        Item item = Item.getItemFromBlock(block);
        long total = 0;
        for (MaterialListEntry entry : list.getAllMaterials()) if (entry.getStack().getItem() == item) total += entry.getTotalCount();
        return total;
    }

    /** Presses Replace on the material list row of the block, found by its item and the button's label. */
    private static String pressReplace(Runner.Context c, Block block) throws Exception {
        if (!(c.mc.currentScreen instanceof MaterialListScreen)) return "the material list is not open";
        Item item = Item.getItemFromBlock(block);
        for (BaseListEntryWidget widget : ((MaterialListScreen) c.mc.currentScreen).getListWidget().getEntryWidgetList()) {
            if (widget instanceof MaterialListEntryWidget && ((MaterialListEntryWidget) widget).getData().getStack().getItem() == item) {
                ConvertTests.press(c, widget, "litematica_upgraded.gui.button.material.replace");
                return null;
            }
        }
        return "no material list row for " + block.getRegistryName() + " yet";
    }

    /** The file Scenario wrote the test's schematic to. */
    private static Path schematicFile(Scenario s) {
        return Paths.get("selftest-tmp").toAbsolutePath().resolve(s.id.replace('/', '-') + ".litematic");
    }

    /** Picks a block by name in the open block picker and confirms with its OK button. */
    private static String pick(Runner.Context c, Block block) throws Exception {
        if (!(c.mc.currentScreen instanceof StringListSelectionScreen)) return "the block picker did not open (" + c.mc.currentScreen + ")";
        StringListSelectionScreen picker = (StringListSelectionScreen) c.mc.currentScreen;
        String name = block.getRegistryName().toString();
        c.check(picker.getStrings().contains(name), "the block picker does not offer " + name);
        c.check(!picker.getStrings().contains("minecraft:air"), "Replace offers air");
        picker.getListWidget().getEntrySelectionHandler().setSelectedEntry(name);
        ConvertTests.press(c, picker, "malilib.button.misc.ok.caps");
        return null;
    }

    /** Through the screens: oak stairs become stone brick stairs (facing and half kept) and back; the file on disk never changes. */
    private static void replaceThroughTheList(Runner.Registry r) {
        Scenario s = new Scenario("material/replace/list-button-swaps-stairs-keeping-facing-and-back");
        s.give = false;
        s.autoEnable = false;
        s.stable = 1;
        s.maxTicks = 1200;
        s.put(0, 0, 0, WEST_STAIRS).put(1, 0, 0, WEST_STAIRS).put(2, 0, 0, TOP_EAST_STAIRS);
        s.put(0, 0, 1, Blocks.PLANKS.getDefaultState()).put(1, 0, 1, Blocks.PLANKS.getDefaultState());
        BlockPos[] stairs = {new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)};
        BlockPos[] planks = {new BlockPos(0, 0, 1), new BlockPos(1, 0, 1)};
        int[] phase = {0};
        byte[][] file = {null};
        MaterialListBase[] list = {null};
        s.check = (c, sc) -> {
            try {
                // 0 open the list, 1 Replace on oak stairs, 2 pick stone brick stairs, 3 check; 4 Replace on stone brick stairs, 5 pick oak stairs, 6 check
                boolean back = phase[0] >= 4;
                Block now = back ? Blocks.OAK_STAIRS : Blocks.STONE_BRICK_STAIRS, old = back ? Blocks.STONE_BRICK_STAIRS : Blocks.OAK_STAIRS;
                String problem;
                switch (phase[0]) {
                    case 0:
                        file[0] = Files.readAllBytes(schematicFile(sc));
                        list[0] = sc.placement().getMaterialList();
                        BaseScreen.openScreen(new MaterialListScreen(list[0]));
                        phase[0]++;
                        return "opening the material list";
                    case 1:
                    case 4:
                        if (total(list[0], old) != 3) return "the material list does not count 3 " + old.getRegistryName() + " yet";
                        problem = pressReplace(c, old);
                        if (problem != null) return problem;
                        phase[0]++;
                        return "Replace pressed";
                    case 2:
                    case 5:
                        problem = pick(c, now);
                        if (problem != null) return problem;
                        phase[0]++;
                        return "block picked";
                    default:
                        problem = ghostsAre(sc, now, stairs);
                        if (problem == null) problem = ghostsAre(sc, Blocks.PLANKS, planks);
                        if (problem != null) return problem;
                        if (total(list[0], now) != 3 || total(list[0], old) != 0) return "the material list counts " + total(list[0], now) + " " + now.getRegistryName() + " and " + total(list[0], old) + " " + old.getRegistryName();
                        if (total(list[0], Blocks.PLANKS) != 2) return "the planks count changed: " + total(list[0], Blocks.PLANKS);
                        c.check(Arrays.equals(file[0], Files.readAllBytes(schematicFile(sc))), "the schematic file on disk changed");
                        c.check(sc.placement().getLoadedSchematic().wasModifiedSinceSaved(), "the loaded schematic is not marked as changed");
                        if (!back) {
                            phase[0]++;
                            return "replaced; now back";
                        }
                        Runner.dismissPopups(c.mc);
                        return null;
                }
            } catch (AssertionError e) {
                throw e;
            } catch (Exception e) {
                throw new AssertionError("the Replace flow failed: " + e, e);
            }
        };
        register(r, s);
    }

    /** Through the code: planks become stone bricks; the printer builds stone bricks; replacing back gives planks again. */
    private static void replaceAndPrint(Runner.Registry r) {
        Scenario s = new Scenario("material/replace/printer-builds-the-new-block-nothing-outside-changes");
        s.give = false;
        s.autoEnable = false;
        s.stable = 5;
        s.maxTicks = 800;
        for (int x = 0; x < 3; x++) s.put(x, 0, 0, Blocks.PLANKS.getDefaultState());
        s.extraCommands.add("give @s minecraft:stonebrick 5");
        BlockPos[] all = {new BlockPos(0, 0, 0), new BlockPos(1, 0, 0), new BlockPos(2, 0, 0)};
        int[] phase = {0};
        s.check = (c, sc) -> {
            switch (phase[0]) {
                case 0:
                    c.check(MaterialReplace.replace(sc.placement(), new ItemStack(Blocks.PLANKS), Blocks.STONEBRICK) == 3, "Replace did not change the 3 planks");
                    phase[0]++;
                    return "replaced";
                case 1: {
                    String problem = ghostsAre(sc, Blocks.STONEBRICK, all);
                    if (problem != null) return problem;
                    PrinterConfigs.PRINTER_ENABLED.setBooleanValue(true);
                    phase[0]++;
                    return "printing";
                }
                case 2: {
                    for (BlockPos rel : all) {
                        if (c.mc.world.getBlockState(sc.world(rel)).getBlock() != Blocks.STONEBRICK) return "the printer has not built stone bricks at " + rel + " yet";
                    }
                    PrinterConfigs.PRINTER_ENABLED.setBooleanValue(false);
                    for (BlockPos rel : new BlockPos[] {new BlockPos(3, 0, 0), new BlockPos(0, 0, 1), new BlockPos(0, 1, 0)}) {
                        c.check(Scenario.isAir(c, sc, rel) && ghost(sc, rel).getBlock() == Blocks.AIR, "something changed outside the placement at " + rel);
                    }
                    c.check(MaterialReplace.replace(sc.placement(), new ItemStack(Blocks.STONEBRICK), Blocks.PLANKS) == 3, "replacing back did not change the 3 blocks");
                    phase[0]++;
                    return "replaced back";
                }
                default:
                    return ghostsAre(sc, Blocks.PLANKS, all);
            }
        };
        register(r, s);
    }

    private static void register(Runner.Registry r, Scenario s) {
        r.waitFor(s.id, s.maxTicks + 300, s::start, s::poll);
    }
}
