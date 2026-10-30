package com.useleess.litematicaupgraded.selftest;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Blocks;
import net.minecraft.init.Enchantments;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.Loader;

import com.useleess.litematicaupgraded.convert.MissingBlocks;
import com.useleess.litematicaupgraded.convert.gui.MissingBlocksScreen;
import com.useleess.litematicaupgraded.litematica.config.Configs;
import com.useleess.litematicaupgraded.litematica.data.SchematicHolder;
import com.useleess.litematicaupgraded.litematica.gui.SchematicBrowserScreen;
import com.useleess.litematicaupgraded.litematica.schematic.LoadedSchematic;
import com.useleess.litematicaupgraded.litematica.schematic.SchematicRegion;
import com.useleess.litematicaupgraded.litematica.schematic.container.BlockContainer;
import com.useleess.litematicaupgraded.litematica.schematic.data.EntityData;
import com.useleess.litematicaupgraded.litematica.util.LitematicaDirectories;
import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.malilib.gui.widget.button.GenericButton;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntry;
import com.useleess.litematicaupgraded.malilib.util.StringUtils;
import com.useleess.litematicaupgraded.malilib.util.data.tag.CompoundData;
import com.useleess.litematicaupgraded.malilib.util.data.tag.converter.DataConverterNbt;

/**
 * convert/: schematic files of newer Minecraft versions (written by ModernSchematicWriter) loaded through Litematica's
 * own loader and browser: every block with its state, container items, sign text, entities, and the missing-blocks
 * window (driven by button name) with the file on disk afterwards. Expected 1.12 states are built from vanilla's own
 * properties, not from the mod's tables.
 */
final class ConvertTests {
    private static final String DIR_NAME = "lu-convert-tests";
    private static final String CHEST = "minecraft:chest[facing=north,type=single,waterlogged=false]";

    private ConvertTests() {}

    static Path dir() throws IOException {
        Path dir = LitematicaDirectories.getSchematicsBaseDirectory().resolve(DIR_NAME);
        Files.createDirectories(dir);
        return dir;
    }

    /** Removes the test files (they are in the player's schematics folder while the tests run). */
    static void cleanup() {
        try {
            Path dir = LitematicaDirectories.getSchematicsBaseDirectory().resolve(DIR_NAME);
            if (Files.isDirectory(dir)) {
                try (Stream<Path> files = Files.list(dir)) {
                    for (Path file : (Iterable<Path>) files::iterator) Files.deleteIfExists(file);
                }
                Files.deleteIfExists(dir);
            }
            Files.deleteIfExists(Loader.instance().getConfigDir().toPath().resolve("litematica_upgraded_missing_blocks.json"));
        } catch (IOException | RuntimeException e) {
            System.out.println("convert/ cleanup: " + e);
        }
    }

    // ---- helpers ----

    private static SchematicRegion load(Path file) {
        Optional<LoadedSchematic> loaded = LoadedSchematic.tryLoadSchematic(file);
        if (!loaded.isPresent()) throw new AssertionError("Litematica could not load " + file.getFileName());
        return loaded.get().schematic.getRegions().values().iterator().next();
    }

    private static IBlockState at(SchematicRegion region, int x) {
        BlockContainer container = region.getBlockContainer();
        return container.getBlockState(x, 0, 0).vanillaState();
    }

    static IBlockState state(Block block, String... properties) {
        IBlockState state = block.getDefaultState();
        for (String property : properties) {
            String key = property.substring(0, property.indexOf('=')), value = property.substring(property.indexOf('=') + 1);
            boolean found = false;
            for (IProperty<?> candidate : block.getBlockState().getProperties()) {
                if (candidate.getName().equals(key)) {
                    state = with(state, candidate, value);
                    found = true;
                }
            }
            if (!found) throw new AssertionError("no property " + key + " on " + block);
        }
        return state;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IBlockState with(IBlockState state, IProperty property, String value) {
        return state.withProperty(property, (Comparable) property.parseValue(value).get());
    }

    private static void eq(Runner.Context c, Object expected, Object got, String what) {
        c.check(expected.equals(got), what + ": wanted " + expected + ", got " + got);
    }

    private static NBTTagCompound vanilla(CompoundData data) {
        return DataConverterNbt.toVanillaCompound(data);
    }

    private static List<String> list(String... states) {
        return Arrays.asList(states);
    }

    /** Writes a .litematic of the states, loads it and compares every cell with the expected states. */
    private static void litematic(Runner.Context c, int dataVersion, List<String> modern, IBlockState... expected) throws IOException {
        Path file = ModernSchematicWriter.litematic(dir().resolve("states-" + dataVersion + "-" + modern.hashCode() + ".litematic"), dataVersion, modern, new ModernSchematicWriter.Extras());
        SchematicRegion region = load(file);
        for (int i = 0; i < expected.length; i++) eq(c, expected[i], at(region, i), modern.get(i) + " in a " + dataVersion + " file");
    }

    private static IBlockState[] states(IBlockState... states) {
        return states;
    }

    private static NBTTagCompound tile(SchematicRegion region, int x) {
        return vanilla(region.getBlockEntityMap().get(new BlockPos(x, 0, 0)));
    }

    /** The stack in one slot of a 1.12 item list (the test fails when the slot is empty). */
    private static NBTTagCompound slot(Runner.Context c, NBTTagList items, int slot) {
        for (int i = 0; i < items.tagCount(); i++) {
            if (items.getCompoundTagAt(i).getByte("Slot") == slot) return items.getCompoundTagAt(i);
        }
        c.check(false, "slot " + slot + " is empty");
        return null;
    }

    private static void stack(Runner.Context c, NBTTagCompound stack, String id, int count, int damage, String what) {
        eq(c, id, stack.getString("id"), what + " item");
        eq(c, count, (int) stack.getByte("Count"), what + " count");
        eq(c, damage, (int) stack.getShort("Damage"), what + " damage");
    }

    /** The level of an enchantment on a 1.12 stack (0 when it has none). */
    private static int level(NBTTagCompound stack, Enchantment enchantment) {
        NBTTagList list = stack.getCompoundTag("tag").getTagList("ench", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            if (list.getCompoundTagAt(i).getShort("id") == Enchantment.getEnchantmentID(enchantment)) return list.getCompoundTagAt(i).getShort("lvl");
        }
        return 0;
    }

    // ---- the window ----

    @SuppressWarnings("unchecked")
    private static <T> T field(Object target, Class<?> type, String name) throws Exception {
        for (Class<?> k = target.getClass(); k != null; k = k.getSuperclass()) {
            try {
                Field field = k.getDeclaredField(name);
                field.setAccessible(true);
                return (T) field.get(target);
            } catch (NoSuchFieldException e) {
                // look further up
            }
        }
        throw new NoSuchFieldException(name + " in " + target.getClass());
    }

    /** Presses the button of a screen with this label (a translation key's text), found by name. */
    static void press(Runner.Context c, Object screen, String key) throws Exception {
        String label = StringUtils.translate(key);
        List<String> seen = new ArrayList<>();
        for (Class<?> k = screen.getClass(); k != null; k = k.getSuperclass()) {
            for (Field field : k.getDeclaredFields()) {
                if (!GenericButton.class.isAssignableFrom(field.getType())) continue;
                field.setAccessible(true);
                GenericButton button = (GenericButton) field.get(screen);
                Field text = GenericButton.class.getDeclaredField("fullDisplayString");
                text.setAccessible(true);
                String shown = (String) text.get(button);
                seen.add(shown);
                if (label.equals(shown)) {
                    Field listener = GenericButton.class.getDeclaredField("actionListener");
                    listener.setAccessible(true);
                    Object action = listener.get(button);
                    for (java.lang.reflect.Method method : action.getClass().getMethods()) {
                        if (method.getName().equals("actionPerformedWithButton")) {
                            method.setAccessible(true);
                            method.invoke(action, 0, button);
                            return;
                        }
                    }
                }
            }
        }
        throw new AssertionError("no button '" + label + "' on " + screen.getClass().getSimpleName() + ", buttons: " + seen);
    }

    /** Opens the schematic browser in the test folder, selects the file and presses its Load button. */
    private static void browserLoad(Runner.Context c, Path file) throws Exception {
        SchematicBrowserScreen browser = new SchematicBrowserScreen();
        BaseScreen.openScreen(browser);
        browser.getListWidget().switchToDirectory(file.getParent());
        DirectoryEntry wanted = null;
        for (DirectoryEntry entry : browser.getListWidget().getFilteredDataList()) {
            if (entry.getName().equals(file.getFileName().toString())) wanted = entry;
        }
        c.check(wanted != null, "the browser does not list " + file.getFileName());
        // a click on the entry: it becomes the selected one, as the browser's load button expects
        browser.getListWidget().getEntrySelectionHandler().clickEntry(browser.getListWidget().getEntrySelectionHandler().getDataList().indexOf(wanted));
        press(c, browser, "litematica.button.schematic_browser.load_schematic");
    }

    private static MissingBlocksScreen window(Runner.Context c) {
        c.check(c.mc.currentScreen instanceof MissingBlocksScreen, "the missing-blocks window is not open (screen: " + c.mc.currentScreen + ")");
        return (MissingBlocksScreen) c.mc.currentScreen;
    }

    private static void noWindow(Runner.Context c) {
        c.check(!(c.mc.currentScreen instanceof MissingBlocksScreen), "the missing-blocks window opened");
    }

    private static final List<String> WITH_MISSING = list("minecraft:sculk", "minecraft:sculk", "minecraft:tuff",
        "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]");

    interface Body {
        void run() throws Exception;
    }

    private static void guarded(Runner.Context c, Body body) throws Exception {
        boolean placement = Configs.Internal.CREATE_PLACEMENT_ON_LOAD.getBooleanValue();
        Configs.Internal.CREATE_PLACEMENT_ON_LOAD.setBooleanValue(false);
        try {
            body.run();
        } finally {
            Configs.Internal.CREATE_PLACEMENT_ON_LOAD.setBooleanValue(placement);
            SchematicHolder.INSTANCE.clearLoadedSchematics();
            MissingBlocks.choose(forget());
            Runner.dismissPopups(c.mc);
        }
    }

    private static Map<String, String> forget() {
        Map<String, String> map = new java.util.LinkedHashMap<>();
        map.put("minecraft:sculk", null);
        map.put("minecraft:tuff", null);
        return map;
    }

    // ---- the tests ----

    static void register(Runner.Registry r) {
        // convert/litematic/: Litematica's own .litematic loader on files of newer versions
        for (int version : new int[] {ModernSchematicWriter.V1_13, ModernSchematicWriter.V1_16, ModernSchematicWriter.V1_20, ModernSchematicWriter.V1_21_5}) {
            r.add("convert/litematic/data-version-" + version, c -> litematic(c, version,
                list("minecraft:birch_planks", "minecraft:red_wool", "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]",
                    "minecraft:stone_slab[type=top,waterlogged=false]", "minecraft:oak_log[axis=x]", "minecraft:air"),
                state(Blocks.PLANKS, "variant=birch"), state(Blocks.WOOL, "color=red"), state(Blocks.OAK_STAIRS, "facing=east", "half=top"),
                state(Blocks.STONE_SLAB, "half=top", "variant=stone"), state(Blocks.LOG, "variant=oak", "axis=x"), Blocks.AIR.getDefaultState()));
        }
        r.add("convert/litematic/1.12-file-loads-as-before", c -> {
            Path file = ModernSchematicWriter.litematic(dir().resolve("old.litematic"), 1343,
                list("minecraft:wool[color=red]", "minecraft:log[axis=z,variant=birch]", "minecraft:stone"), new ModernSchematicWriter.Extras());
            // a 1.12 file with the older version number
            NBTTagCompound root = net.minecraft.nbt.CompressedStreamTools.readCompressed(Files.newInputStream(file));
            root.setInteger("Version", 4);
            try (java.io.OutputStream out = Files.newOutputStream(file)) {
                net.minecraft.nbt.CompressedStreamTools.writeCompressed(root, out);
            }
            SchematicRegion region = load(file);
            eq(c, state(Blocks.WOOL, "color=red"), at(region, 0), "1.12 wool");
            eq(c, state(Blocks.LOG, "variant=birch", "axis=z"), at(region, 1), "1.12 log");
            eq(c, Blocks.STONE.getDefaultState(), at(region, 2), "1.12 stone");
        });

        // convert/sponge/: Sponge .schem, versions 2 and 3
        r.add("convert/sponge/schem-v2", c -> {
            ModernSchematicWriter.Extras extras = new ModernSchematicWriter.Extras().tile(1, "{id:\"minecraft:chest\",Items:[{Slot:0b,id:\"minecraft:cod\",Count:2b}]}");
            Path file = ModernSchematicWriter.sponge(dir().resolve("v2.schem"), 2, ModernSchematicWriter.V1_16,
                list("minecraft:oak_stairs[facing=west,half=top,shape=straight,waterlogged=false]", CHEST, "minecraft:orange_wool"), extras);
            SchematicRegion region = load(file);
            eq(c, state(Blocks.OAK_STAIRS, "facing=west", "half=top"), at(region, 0), "stairs");
            eq(c, state(Blocks.CHEST, "facing=north"), at(region, 1), "chest");
            eq(c, state(Blocks.WOOL, "color=orange"), at(region, 2), "wool");
            NBTTagList items = vanilla(region.getBlockEntityMap().get(new BlockPos(1, 0, 0))).getTagList("Items", 10);
            eq(c, "minecraft:fish", items.getCompoundTagAt(0).getString("id"), "the cod in the chest");
            eq(c, 2, (int) items.getCompoundTagAt(0).getByte("Count"), "its count");
        });
        r.add("convert/sponge/schem-v3", c -> {
            ModernSchematicWriter.Extras extras = new ModernSchematicWriter.Extras().tile(1, "{id:\"minecraft:barrel\",Items:[{Slot:1b,id:\"minecraft:red_dye\",count:6}]}");
            Path file = ModernSchematicWriter.sponge(dir().resolve("v3.schem"), 3, ModernSchematicWriter.V1_21,
                list("minecraft:red_wool", "minecraft:barrel[facing=up,open=false]", "minecraft:lever[face=wall,facing=east,powered=false]"), extras);
            SchematicRegion region = load(file);
            eq(c, state(Blocks.WOOL, "color=red"), at(region, 0), "wool");
            eq(c, Blocks.CHEST, at(region, 1).getBlock(), "the barrel as a chest");
            eq(c, state(Blocks.LEVER, "facing=east"), at(region, 2), "lever");
            NBTTagCompound dye = vanilla(region.getBlockEntityMap().get(new BlockPos(1, 0, 0))).getTagList("Items", 10).getCompoundTagAt(0);
            eq(c, "minecraft:dye", dye.getString("id"), "the dye item");
            eq(c, 1, (int) dye.getShort("Damage"), "its colour (red)");
            eq(c, 6, (int) dye.getByte("Count"), "its count");
        });

        // convert/structure/: vanilla structure templates
        r.add("convert/structure/modern-nbt", c -> {
            ModernSchematicWriter.Extras extras = new ModernSchematicWriter.Extras()
                .tile(1, "{id:\"minecraft:chest\",Items:[{Slot:9b,id:\"minecraft:white_bed\",Count:1b}]}")
                .entity("{id:\"minecraft:snow_golem\",Pos:[2.5d,0.5d,0.5d]}");
            Path file = ModernSchematicWriter.structure(dir().resolve("modern.nbt"), ModernSchematicWriter.V1_16,
                list("minecraft:birch_planks", "minecraft:chest[facing=east,type=single,waterlogged=false]", "minecraft:observer[facing=up,powered=false]"), extras);
            SchematicRegion region = load(file);
            eq(c, state(Blocks.PLANKS, "variant=birch"), at(region, 0), "planks");
            eq(c, state(Blocks.CHEST, "facing=east"), at(region, 1), "chest");
            eq(c, state(Blocks.OBSERVER, "facing=up"), at(region, 2), "observer");
            NBTTagCompound bed = vanilla(region.getBlockEntityMap().get(new BlockPos(1, 0, 0))).getTagList("Items", 10).getCompoundTagAt(0);
            eq(c, "minecraft:bed", bed.getString("id"), "the bed item");
            eq(c, 1, region.getEntityList().size(), "entities");
            eq(c, "minecraft:snowman", vanilla(region.getEntityList().get(0).data).getString("id"), "the snow golem under its 1.12 name");
        });

        // convert/states/: the states come out right
        r.add("convert/states/stairs-and-slabs", c -> litematic(c, ModernSchematicWriter.V1_16,
            list("minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]", "minecraft:stone_brick_stairs[facing=south,half=top,shape=straight,waterlogged=false]",
                "minecraft:oak_slab[type=bottom,waterlogged=false]", "minecraft:oak_slab[type=top,waterlogged=false]", "minecraft:oak_slab[type=double,waterlogged=false]",
                "minecraft:stone_slab[type=bottom,waterlogged=false]", "minecraft:sandstone_slab[type=top,waterlogged=false]"),
            state(Blocks.OAK_STAIRS, "facing=north", "half=bottom"), state(Blocks.STONE_BRICK_STAIRS, "facing=south", "half=top"),
            state(Blocks.WOODEN_SLAB, "variant=oak", "half=bottom"), state(Blocks.WOODEN_SLAB, "variant=oak", "half=top"), state(Blocks.DOUBLE_WOODEN_SLAB, "variant=oak"),
            state(Blocks.STONE_SLAB, "variant=stone", "half=bottom"), state(Blocks.STONE_SLAB, "variant=sandstone", "half=top")));
        r.add("convert/states/logs-axis-and-planks", c -> litematic(c, ModernSchematicWriter.V1_16,
            list("minecraft:spruce_log[axis=x]", "minecraft:birch_log[axis=y]", "minecraft:jungle_log[axis=z]", "minecraft:acacia_log[axis=y]", "minecraft:dark_oak_log[axis=x]",
                "minecraft:spruce_planks", "minecraft:dark_oak_planks"),
            state(Blocks.LOG, "variant=spruce", "axis=x"), state(Blocks.LOG, "variant=birch", "axis=y"), state(Blocks.LOG, "variant=jungle", "axis=z"),
            state(Blocks.LOG2, "variant=acacia", "axis=y"), state(Blocks.LOG2, "variant=dark_oak", "axis=x"),
            state(Blocks.PLANKS, "variant=spruce"), state(Blocks.PLANKS, "variant=dark_oak")));
        r.add("convert/states/flattened-colours", c -> {
            String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
            List<String> modern = new ArrayList<>();
            List<IBlockState> expected = new ArrayList<>();
            for (String color : colors) {
                String old = color.equals("light_gray") ? "silver" : color;
                modern.add("minecraft:" + color + "_wool");
                expected.add(state(Blocks.WOOL, "color=" + old));
                modern.add("minecraft:" + color + "_concrete");
                expected.add(state(Blocks.CONCRETE, "color=" + old));
                modern.add("minecraft:" + color + "_stained_glass");
                expected.add(state(Blocks.STAINED_GLASS, "color=" + old));
                modern.add("minecraft:" + color + "_terracotta");
                expected.add(state(Blocks.STAINED_HARDENED_CLAY, "color=" + old));
            }
            litematic(c, ModernSchematicWriter.V1_20, modern, expected.toArray(new IBlockState[0]));
        });
        r.add("convert/states/redstone", c -> {
            Path file = ModernSchematicWriter.litematic(dir().resolve("redstone.litematic"), ModernSchematicWriter.V1_16,
                list("minecraft:repeater[delay=3,facing=east,locked=false,powered=false]", "minecraft:comparator[facing=west,mode=subtract,powered=false]",
                    "minecraft:redstone_wire[east=none,north=side,power=7,south=none,west=none]", "minecraft:lever[face=floor,facing=north,powered=true]",
                    "minecraft:piston[extended=false,facing=up]", "minecraft:observer[facing=south,powered=false]"), new ModernSchematicWriter.Extras());
            SchematicRegion region = load(file);
            IBlockState repeater = at(region, 0), comparator = at(region, 1), wire = at(region, 2), lever = at(region, 3);
            eq(c, Blocks.UNPOWERED_REPEATER, repeater.getBlock(), "repeater block");
            eq(c, 3, (int) repeater.getValue(net.minecraft.block.BlockRedstoneRepeater.DELAY), "repeater delay");
            eq(c, Blocks.UNPOWERED_COMPARATOR, comparator.getBlock(), "comparator block");
            eq(c, net.minecraft.block.BlockRedstoneComparator.Mode.SUBTRACT, comparator.getValue(net.minecraft.block.BlockRedstoneComparator.MODE), "comparator mode");
            eq(c, 7, (int) wire.getValue(net.minecraft.block.BlockRedstoneWire.POWER), "wire power");
            eq(c, Blocks.LEVER, lever.getBlock(), "lever block");
            eq(c, true, lever.getValue(net.minecraft.block.BlockLever.POWERED), "lever powered");
            eq(c, state(Blocks.PISTON, "facing=up"), at(region, 4), "piston");
            eq(c, state(Blocks.OBSERVER, "facing=south"), at(region, 5), "observer");
        });
        r.add("convert/states/doors-beds-and-wall-blocks", c -> litematic(c, ModernSchematicWriter.V1_16,
            list("minecraft:iron_door[facing=east,half=lower,hinge=right,open=true,powered=false]", "minecraft:white_bed[facing=south,occupied=false,part=foot]",
                "minecraft:stone_button[face=wall,facing=east,powered=false]", "minecraft:wall_torch[facing=west]", "minecraft:torch",
                "minecraft:oak_wall_sign[facing=north,waterlogged=false]", "minecraft:oak_sign[rotation=4,waterlogged=false]"),
            state(Blocks.IRON_DOOR, "facing=east", "half=lower", "open=true"), state(Blocks.BED, "facing=south", "part=foot"),
            state(Blocks.STONE_BUTTON, "facing=east"), state(Blocks.TORCH, "facing=west"), state(Blocks.TORCH, "facing=up"),
            state(Blocks.WALL_SIGN, "facing=north"), state(Blocks.STANDING_SIGN, "rotation=4")));
        r.add("convert/states/renamed-blocks", c -> litematic(c, ModernSchematicWriter.V1_21,
            list("minecraft:spawner", "minecraft:grass_block[snowy=false]", "minecraft:dirt_path", "minecraft:short_grass", "minecraft:cobweb", "minecraft:magma_block"),
            Blocks.MOB_SPAWNER.getDefaultState(), Blocks.GRASS.getDefaultState(), Blocks.GRASS_PATH.getDefaultState(), state(Blocks.TALLGRASS, "type=tall_grass"),
            Blocks.WEB.getDefaultState(), Blocks.MAGMA.getDefaultState()));

        // convert/nbt/: block entities and entities
        r.add("convert/nbt/chest-items-and-sign-text", c -> {
            ModernSchematicWriter.Extras extras = new ModernSchematicWriter.Extras()
                .tile(0, "{id:\"minecraft:chest\",CustomName:'{\"text\":\"Loot\"}',Items:[{Slot:0b,id:\"minecraft:red_dye\",Count:3b},{Slot:5b,id:\"minecraft:birch_planks\",Count:64b},"
                    + "{Slot:7b,id:\"minecraft:diamond_sword\",Count:1b,tag:{Damage:30,display:{Name:'{\"text\":\"Edge\"}'}}}]}")
                .tile(1, "{id:\"minecraft:sign\",front_text:{color:\"black\",has_glowing_text:0b,messages:['{\"text\":\"one\"}','{\"text\":\"t\",\"extra\":[{\"text\":\"wo\"}]}','{\"text\":\"\"}','{\"text\":\"four\"}']}}");
            Path file = ModernSchematicWriter.litematic(dir().resolve("nbt.litematic"), ModernSchematicWriter.V1_20,
                list(CHEST, "minecraft:oak_sign[rotation=4,waterlogged=false]"), extras);
            SchematicRegion region = load(file);
            NBTTagCompound chest = vanilla(region.getBlockEntityMap().get(new BlockPos(0, 0, 0)));
            eq(c, "minecraft:chest", chest.getString("id"), "chest id");
            eq(c, "Loot", chest.getString("CustomName"), "chest name made plain");
            NBTTagList items = chest.getTagList("Items", 10);
            eq(c, 3, items.tagCount(), "item stacks");
            eq(c, "minecraft:dye", items.getCompoundTagAt(0).getString("id"), "dye");
            eq(c, 1, (int) items.getCompoundTagAt(0).getShort("Damage"), "red dye damage");
            eq(c, "minecraft:planks", items.getCompoundTagAt(1).getString("id"), "planks");
            eq(c, 2, (int) items.getCompoundTagAt(1).getShort("Damage"), "birch planks damage");
            eq(c, 5, (int) items.getCompoundTagAt(1).getByte("Slot"), "slot kept");
            eq(c, 30, (int) items.getCompoundTagAt(2).getShort("Damage"), "sword damage");
            eq(c, "Edge", items.getCompoundTagAt(2).getCompoundTag("tag").getCompoundTag("display").getString("Name"), "sword name");
            NBTTagCompound sign = vanilla(region.getBlockEntityMap().get(new BlockPos(1, 0, 0)));
            eq(c, "minecraft:sign", sign.getString("id"), "sign id");
            String[] lines = {"one", "two", "", "four"};
            for (int i = 0; i < 4; i++) {
                eq(c, lines[i], net.minecraft.util.text.ITextComponent.Serializer.jsonToComponent(sign.getString("Text" + (i + 1))).getUnformattedText(), "sign line " + (i + 1));
            }
        });
        r.add("convert/nbt/entities", c -> {
            ModernSchematicWriter.Extras extras = new ModernSchematicWriter.Extras()
                .entity("{id:\"minecraft:armor_stand\",Pos:[0.5d,0.0d,0.5d],CustomName:'{\"text\":\"Stand\"}',HandItems:[{id:\"minecraft:cooked_cod\",count:2},{}]}")
                .entity("{id:\"minecraft:snow_golem\",Pos:[1.5d,0.0d,0.5d]}")
                .entity("{id:\"minecraft:item_frame\",Facing:1b,Pos:[0.5d,1.0d,0.5d]}");
            Path file = ModernSchematicWriter.litematic(dir().resolve("entities.litematic"), ModernSchematicWriter.V1_21, list("minecraft:stone", "minecraft:stone"), extras);
            SchematicRegion region = load(file);
            List<EntityData> entities = region.getEntityList();
            eq(c, 2, entities.size(), "entities (the floor item frame is left out)");
            NBTTagCompound stand = vanilla(entities.get(0).data);
            eq(c, "Stand", stand.getString("CustomName"), "armor stand name made plain");
            NBTTagCompound hand = stand.getTagList("HandItems", 10).getCompoundTagAt(0);
            eq(c, "minecraft:cooked_fish", hand.getString("id"), "the item in its hand");
            eq(c, "minecraft:snowman", vanilla(entities.get(1).data).getString("id"), "snow golem under its 1.12 name");
        });
        r.add("convert/nbt/barrel-and-shulker-box-contents", c -> {
            ModernSchematicWriter.Extras extras = new ModernSchematicWriter.Extras()
                .tile(0, "{id:\"minecraft:barrel\",Items:[{Slot:1b,id:\"minecraft:lapis_lazuli\",count:9}]}")
                .tile(1, "{id:\"minecraft:shulker_box\",Items:[{Slot:2b,id:\"minecraft:gray_dye\",count:4}]}");
            Path file = ModernSchematicWriter.litematic(dir().resolve("containers.litematic"), ModernSchematicWriter.V1_21,
                list("minecraft:barrel[facing=up,open=false]", "minecraft:purple_shulker_box[facing=up]"), extras);
            SchematicRegion region = load(file);
            eq(c, "minecraft:chest", vanilla(region.getBlockEntityMap().get(new BlockPos(0, 0, 0))).getString("id"), "the barrel's block entity");
            NBTTagCompound lapis = vanilla(region.getBlockEntityMap().get(new BlockPos(0, 0, 0))).getTagList("Items", 10).getCompoundTagAt(0);
            eq(c, "minecraft:dye", lapis.getString("id"), "lapis item");
            eq(c, 4, (int) lapis.getShort("Damage"), "lapis damage");
            eq(c, Blocks.PURPLE_SHULKER_BOX, at(region, 1).getBlock(), "the shulker box colour");
            NBTTagCompound gray = vanilla(region.getBlockEntityMap().get(new BlockPos(1, 0, 0))).getTagList("Items", 10).getCompoundTagAt(0);
            eq(c, 8, (int) gray.getShort("Damage"), "gray dye damage");
        });
        // convert/inventories/: every container of one file keeps every slot (item, count, damage, enchantment, name)
        r.add("convert/inventories/every-container-keeps-every-slot", c -> {
            ModernSchematicWriter.Extras extras = new ModernSchematicWriter.Extras()
                .tile(0, "{id:\"minecraft:hopper\",Items:[{Slot:0b,id:\"minecraft:stone\",count:7},{Slot:2b,id:\"minecraft:spruce_planks\",count:9},"
                    + "{Slot:4b,id:\"minecraft:cobblestone\",count:2}]}")
                .tile(1, "{id:\"minecraft:furnace\",BurnTime:200s,CookTime:120s,CookTimeTotal:200s,Items:[{Slot:0b,id:\"minecraft:sand\",count:5},"
                    + "{Slot:1b,id:\"minecraft:charcoal\",count:2},{Slot:2b,id:\"minecraft:iron_ingot\",count:3}]}")
                .tile(2, "{id:\"minecraft:dispenser\",Items:[{Slot:0b,id:\"minecraft:arrow\",count:16},{Slot:8b,id:\"minecraft:bow\",count:1,"
                    + "components:{\"minecraft:damage\":100,\"minecraft:enchantments\":{levels:{\"minecraft:power\":3}}}}]}")
                .tile(3, "{id:\"minecraft:dropper\",Items:[{Slot:3b,id:\"minecraft:redstone\",count:12},{Slot:5b,id:\"minecraft:bone\",count:4}]}")
                .tile(4, "{id:\"minecraft:brewing_stand\",Items:[{Slot:0b,id:\"minecraft:potion\",count:1,"
                    + "components:{\"minecraft:potion_contents\":{potion:\"minecraft:healing\"}}},{Slot:4b,id:\"minecraft:blaze_powder\",count:3}]}")
                .tile(5, "{id:\"minecraft:chest\",Items:[{Slot:0b,id:\"minecraft:diamond_sword\",count:1,components:{\"minecraft:damage\":30,"
                    + "\"minecraft:custom_name\":'{\"text\":\"Edge\"}',\"minecraft:enchantments\":{levels:{\"minecraft:sharpness\":2}}}},"
                    + "{Slot:13b,id:\"minecraft:golden_apple\",count:1},{Slot:26b,id:\"minecraft:cooked_beef\",count:8}]}")
                .tile(6, "{id:\"minecraft:trapped_chest\",Items:[{Slot:1b,id:\"minecraft:gunpowder\",count:4},{Slot:7b,id:\"minecraft:emerald\",count:12}]}")
                .tile(7, "{id:\"minecraft:barrel\",Items:[{Slot:2b,id:\"minecraft:bread\",count:5},{Slot:4b,id:\"minecraft:lapis_lazuli\",count:9}]}")
                .tile(8, "{id:\"minecraft:shulker_box\",Items:[{Slot:0b,id:\"minecraft:gray_dye\",count:4},{Slot:26b,id:\"minecraft:apple\",count:1}]}")
                .tile(9, "{id:\"minecraft:jukebox\",RecordItem:{id:\"minecraft:music_disc_cat\",count:1}}")
                .entity("{id:\"minecraft:item_frame\",Facing:3b,Pos:[0.5d,1.5d,0.5d],Item:{id:\"minecraft:iron_pickaxe\",count:1,components:{\"minecraft:damage\":50}}}")
                .entity("{id:\"minecraft:armor_stand\",Pos:[2.5d,0.0d,0.5d],HandItems:[{id:\"minecraft:diamond_sword\",count:1,components:{\"minecraft:damage\":7}},"
                    + "{id:\"minecraft:shield\",count:1}],ArmorItems:[{id:\"minecraft:leather_boots\",count:1},{id:\"minecraft:leather_leggings\",count:1},"
                    + "{id:\"minecraft:leather_chestplate\",count:1},{id:\"minecraft:iron_helmet\",count:1,components:{\"minecraft:custom_name\":'{\"text\":\"Helm\"}',"
                    + "\"minecraft:enchantments\":{levels:{\"minecraft:protection\":2}}}}]}");
            List<String> containers = list("minecraft:hopper[enabled=true,facing=down]", "minecraft:furnace[facing=north,lit=false]",
                "minecraft:dispenser[facing=north,triggered=false]", "minecraft:dropper[facing=north,triggered=false]",
                "minecraft:brewing_stand[has_bottle_0=false,has_bottle_1=false,has_bottle_2=false]", CHEST,
                "minecraft:trapped_chest[facing=north,type=single,waterlogged=false]", "minecraft:barrel[facing=up,open=false]",
                "minecraft:purple_shulker_box[facing=up]", "minecraft:jukebox[has_record=true]");
            SchematicRegion region = load(ModernSchematicWriter.litematic(dir().resolve("inventories.litematic"), ModernSchematicWriter.V1_21, containers, extras));
            eq(c, Blocks.HOPPER, at(region, 0).getBlock(), "hopper block");
            eq(c, Blocks.FURNACE, at(region, 1).getBlock(), "furnace block");
            eq(c, Blocks.DISPENSER, at(region, 2).getBlock(), "dispenser block");
            eq(c, Blocks.DROPPER, at(region, 3).getBlock(), "dropper block");
            eq(c, Blocks.BREWING_STAND, at(region, 4).getBlock(), "brewing stand block");
            eq(c, Blocks.TRAPPED_CHEST, at(region, 6).getBlock(), "trapped chest block");
            eq(c, Blocks.PURPLE_SHULKER_BOX, at(region, 8).getBlock(), "shulker box block");
            eq(c, Blocks.JUKEBOX, at(region, 9).getBlock(), "jukebox block");

            NBTTagList hopper = tile(region, 0).getTagList("Items", 10);
            eq(c, 3, hopper.tagCount(), "hopper stacks");
            stack(c, slot(c, hopper, 0), "minecraft:stone", 7, 0, "hopper slot 0");
            stack(c, slot(c, hopper, 2), "minecraft:planks", 9, 1, "hopper slot 2");
            stack(c, slot(c, hopper, 4), "minecraft:cobblestone", 2, 0, "hopper slot 4");

            NBTTagCompound furnace = tile(region, 1);
            eq(c, 0, (int) furnace.getShort("BurnTime"), "furnace loads cold: burn time");
            eq(c, 0, (int) furnace.getShort("CookTime"), "furnace loads cold: cook time");
            NBTTagList fuel = furnace.getTagList("Items", 10);
            eq(c, 3, fuel.tagCount(), "furnace stacks");
            stack(c, slot(c, fuel, 0), "minecraft:sand", 5, 0, "furnace input");
            stack(c, slot(c, fuel, 1), "minecraft:coal", 2, 1, "furnace fuel (charcoal)");
            stack(c, slot(c, fuel, 2), "minecraft:iron_ingot", 3, 0, "furnace output");

            NBTTagList dispenser = tile(region, 2).getTagList("Items", 10);
            eq(c, 2, dispenser.tagCount(), "dispenser stacks");
            stack(c, slot(c, dispenser, 0), "minecraft:arrow", 16, 0, "dispenser slot 0");
            NBTTagCompound bow = slot(c, dispenser, 8);
            stack(c, bow, "minecraft:bow", 1, 100, "dispenser bow");
            eq(c, 3, level(bow, Enchantments.POWER), "bow power level");

            NBTTagList dropper = tile(region, 3).getTagList("Items", 10);
            eq(c, 2, dropper.tagCount(), "dropper stacks");
            stack(c, slot(c, dropper, 3), "minecraft:redstone", 12, 0, "dropper slot 3");
            stack(c, slot(c, dropper, 5), "minecraft:bone", 4, 0, "dropper slot 5");

            NBTTagList brewing = tile(region, 4).getTagList("Items", 10);
            eq(c, 2, brewing.tagCount(), "brewing stand stacks");
            NBTTagCompound potion = slot(c, brewing, 0);
            stack(c, potion, "minecraft:potion", 1, 0, "brewing stand bottle");
            eq(c, "minecraft:healing", potion.getCompoundTag("tag").getString("Potion"), "bottle's potion");
            stack(c, slot(c, brewing, 4), "minecraft:blaze_powder", 3, 0, "brewing stand fuel");

            NBTTagList chest = tile(region, 5).getTagList("Items", 10);
            eq(c, 3, chest.tagCount(), "chest stacks");
            NBTTagCompound sword = slot(c, chest, 0);
            stack(c, sword, "minecraft:diamond_sword", 1, 30, "chest sword");
            eq(c, "Edge", sword.getCompoundTag("tag").getCompoundTag("display").getString("Name"), "sword name");
            eq(c, 2, level(sword, Enchantments.SHARPNESS), "sword sharpness level");
            stack(c, slot(c, chest, 13), "minecraft:golden_apple", 1, 0, "chest slot 13");
            stack(c, slot(c, chest, 26), "minecraft:cooked_beef", 8, 0, "chest slot 26");

            NBTTagList trapped = tile(region, 6).getTagList("Items", 10);
            eq(c, 2, trapped.tagCount(), "trapped chest stacks");
            stack(c, slot(c, trapped, 1), "minecraft:gunpowder", 4, 0, "trapped chest slot 1");
            stack(c, slot(c, trapped, 7), "minecraft:emerald", 12, 0, "trapped chest slot 7");

            NBTTagList barrel = tile(region, 7).getTagList("Items", 10);
            eq(c, 2, barrel.tagCount(), "barrel stacks");
            stack(c, slot(c, barrel, 2), "minecraft:bread", 5, 0, "barrel slot 2");
            stack(c, slot(c, barrel, 4), "minecraft:dye", 9, 4, "barrel slot 4 (lapis)");

            NBTTagList shulker = tile(region, 8).getTagList("Items", 10);
            eq(c, 2, shulker.tagCount(), "shulker box stacks");
            stack(c, slot(c, shulker, 0), "minecraft:dye", 4, 8, "shulker slot 0 (gray dye)");
            stack(c, slot(c, shulker, 26), "minecraft:apple", 1, 0, "shulker slot 26");

            stack(c, tile(region, 9).getCompoundTag("RecordItem"), "minecraft:record_cat", 1, 0, "jukebox record");

            List<EntityData> entities = region.getEntityList();
            eq(c, 2, entities.size(), "entities");
            NBTTagCompound frame = vanilla(entities.get(0).data);
            eq(c, "minecraft:item_frame", frame.getString("id"), "item frame id");
            stack(c, frame.getCompoundTag("Item"), "minecraft:iron_pickaxe", 1, 50, "item frame item");
            NBTTagCompound stand = vanilla(entities.get(1).data);
            NBTTagList hand = stand.getTagList("HandItems", 10), armor = stand.getTagList("ArmorItems", 10);
            stack(c, hand.getCompoundTagAt(0), "minecraft:diamond_sword", 1, 7, "armour stand main hand");
            stack(c, hand.getCompoundTagAt(1), "minecraft:shield", 1, 0, "armour stand off hand");
            stack(c, armor.getCompoundTagAt(0), "minecraft:leather_boots", 1, 0, "armour stand boots");
            stack(c, armor.getCompoundTagAt(1), "minecraft:leather_leggings", 1, 0, "armour stand leggings");
            stack(c, armor.getCompoundTagAt(2), "minecraft:leather_chestplate", 1, 0, "armour stand chestplate");
            NBTTagCompound helmet = armor.getCompoundTagAt(3);
            stack(c, helmet, "minecraft:iron_helmet", 1, 0, "armour stand helmet");
            eq(c, "Helm", helmet.getCompoundTag("tag").getCompoundTag("display").getString("Name"), "helmet name");
            eq(c, 2, level(helmet, Enchantments.PROTECTION), "helmet protection level");
        });

        // convert/missing/: the missing-blocks window
        r.add("convert/missing/window-opens-when-the-file-is-loaded", c -> guarded(c, () -> {
            Path file = ModernSchematicWriter.litematic(dir().resolve("open.litematic"), ModernSchematicWriter.V1_21, WITH_MISSING, new ModernSchematicWriter.Extras());
            browserLoad(c, file);
            MissingBlocksScreen window = window(c);
            eq(c, 2, window.getRows().size(), "one row per missing block");
            eq(c, "minecraft:sculk", window.getRows().get(0).name, "the most common block first");
            eq(c, 2L, window.getRows().get(0).count, "its count");
            eq(c, "minecraft:tuff", window.getRows().get(1).name, "then tuff");
            eq(c, 0, SchematicHolder.INSTANCE.getAllSchematics().size(), "nothing is loaded before the answer");
        }));
        r.add("convert/missing/window-is-prefilled-with-guesses-and-saved-picks", c -> guarded(c, () -> {
            Path file = ModernSchematicWriter.litematic(dir().resolve("prefill.litematic"), ModernSchematicWriter.V1_21, WITH_MISSING, new ModernSchematicWriter.Extras());
            browserLoad(c, file);
            for (MissingBlocksScreen.Row row : window(c).getRows()) {
                eq(c, MissingBlocks.replacement(row.name), row.picked, "the pick for " + row.name + " starts as what loading uses");
                eq(c, MissingBlocks.guessed(row.name), row.picked, "the pick for " + row.name + " starts as the guess");
            }
            Runner.dismissPopups(c.mc);
            MissingBlocks.choose(Collections.singletonMap("minecraft:tuff", "minecraft:wool[color=lime]"));
            browserLoad(c, file);
            for (MissingBlocksScreen.Row row : window(c).getRows()) {
                if (row.name.equals("minecraft:tuff")) eq(c, state(Blocks.WOOL, "color=lime"), row.picked, "a saved pick prefills");
            }
        }));
        r.add("convert/missing/use-these-overwrites-the-file-and-asks-no-more", c -> guarded(c, () -> {
            Path file = ModernSchematicWriter.litematic(dir().resolve("overwrite.litematic"), ModernSchematicWriter.V1_21, WITH_MISSING, new ModernSchematicWriter.Extras());
            long before = Files.size(file);
            browserLoad(c, file);
            MissingBlocksScreen window = window(c);
            for (MissingBlocksScreen.Row row : window.getRows()) {
                window.pick(row, row.name.equals("minecraft:sculk") ? Blocks.GOLD_BLOCK.getDefaultState() : Blocks.AIR.getDefaultState());
            }
            press(c, window, "litematica_upgraded.gui.button.missing.use_these");
            noWindow(c);
            c.check(Files.isRegularFile(file), "the file is gone");
            c.check(Files.size(file) != before, "the file on disk was not rewritten");
            eq(c, 1, SchematicHolder.INSTANCE.getAllSchematics().size(), "the converted file is loaded");
            SchematicRegion region = load(file);
            eq(c, Blocks.GOLD_BLOCK.getDefaultState(), at(region, 0), "sculk became the picked gold");
            eq(c, Blocks.GOLD_BLOCK.getDefaultState(), at(region, 1), "both sculk blocks");
            eq(c, Blocks.AIR.getDefaultState(), at(region, 2), "tuff became air");
            eq(c, state(Blocks.OAK_STAIRS, "facing=east", "half=top"), at(region, 3), "the stairs are untouched");
            // the file on disk is the truth: loading it again opens no window
            SchematicHolder.INSTANCE.clearLoadedSchematics();
            browserLoad(c, file);
            noWindow(c);
            eq(c, 1, SchematicHolder.INSTANCE.getAllSchematics().size(), "loaded again without asking");
        }));
        r.add("convert/missing/schem-becomes-a-litematic-and-the-schem-is-deleted", c -> guarded(c, () -> {
            Path file = ModernSchematicWriter.sponge(dir().resolve("convert-me.schem"), 2, ModernSchematicWriter.V1_21, WITH_MISSING, new ModernSchematicWriter.Extras());
            browserLoad(c, file);
            press(c, window(c), "litematica_upgraded.gui.button.missing.use_these");
            noWindow(c);
            Path converted = dir().resolve("convert-me.litematic");
            c.check(Files.isRegularFile(converted), "no same-name .litematic was written");
            c.check(!Files.exists(file), "the .schem was not deleted");
            SchematicRegion region = load(converted);
            StringBuilder cells = new StringBuilder();
            for (int i = 0; i < 4; i++) cells.append(at(region, i)).append(" | ");
            c.check(state(Blocks.OAK_STAIRS, "facing=east", "half=top").equals(at(region, 3)), "the stairs in the converted file; region " + region.getRelativePosition() + " size " + region.getSize() + " cells " + cells);
            eq(c, MissingBlocks.replacement("minecraft:sculk").getBlock(), at(region, 0).getBlock(), "sculk as its stand-in");
            SchematicHolder.INSTANCE.clearLoadedSchematics();
            browserLoad(c, converted);
            noWindow(c);
        }));
        r.add("convert/missing/picks-are-saved-globally", c -> guarded(c, () -> {
            Path file = ModernSchematicWriter.litematic(dir().resolve("picks.litematic"), ModernSchematicWriter.V1_21, WITH_MISSING, new ModernSchematicWriter.Extras());
            browserLoad(c, file);
            MissingBlocksScreen window = window(c);
            for (MissingBlocksScreen.Row row : window.getRows()) {
                if (row.name.equals("minecraft:sculk")) window.pick(row, Blocks.GOLD_BLOCK.getDefaultState());
            }
            press(c, window, "litematica_upgraded.gui.button.missing.use_these");
            eq(c, "minecraft:gold_block", MissingBlocks.choice("minecraft:sculk"), "the pick for sculk");
            Path config = Loader.instance().getConfigDir().toPath().resolve("litematica_upgraded_missing_blocks.json");
            c.check(Files.isRegularFile(config) && new String(Files.readAllBytes(config), StandardCharsets.UTF_8).contains("minecraft:gold_block"),
                "the pick is not in " + config);
            // the next file with sculk starts with the pick
            Path other = ModernSchematicWriter.litematic(dir().resolve("picks-other.litematic"), ModernSchematicWriter.V1_21, list("minecraft:sculk", "minecraft:tuff"), new ModernSchematicWriter.Extras());
            eq(c, Blocks.GOLD_BLOCK.getDefaultState(), at(load(other), 0), "sculk loads as the earlier pick");
        }));
        r.add("convert/missing/no-window-when-placements-come-back-or-nothing-is-missing", c -> guarded(c, () -> {
            // restoring a placement (joining a world) loads the file without the browser: no window
            Path file = ModernSchematicWriter.litematic(dir().resolve("restore.litematic"), ModernSchematicWriter.V1_21, WITH_MISSING, new ModernSchematicWriter.Extras());
            c.check(SchematicHolder.INSTANCE.getOrLoad(file).isPresent(), "the file did not load");
            noWindow(c);
            SchematicHolder.INSTANCE.clearLoadedSchematics();
            // a modern file whose blocks 1.12 all has loads from the browser without a window, converted in memory
            Path complete = ModernSchematicWriter.litematic(dir().resolve("complete.litematic"), ModernSchematicWriter.V1_21,
                list("minecraft:birch_planks", "minecraft:red_wool"), new ModernSchematicWriter.Extras());
            browserLoad(c, complete);
            noWindow(c);
            eq(c, 1, SchematicHolder.INSTANCE.getAllSchematics().size(), "the complete file is loaded");
            // "Load without converting" loads it as it is and leaves the file alone
            SchematicHolder.INSTANCE.clearLoadedSchematics();
            long before = Files.size(file);
            browserLoad(c, file);
            press(c, window(c), "litematica_upgraded.gui.button.missing.skip");
            eq(c, 1, SchematicHolder.INSTANCE.getAllSchematics().size(), "loaded with the guesses");
            eq(c, before, Files.size(file), "the file is not touched");
        }));
        // Forge's data fixer reports version 0: files must still carry 1.12.2's 1343, and converted files get no "future" warnings
        r.add("convert/litematic/data-versions-are-1-12-2-and-converted-files-raise-no-warning", c -> {
            eq(c, com.useleess.litematicaupgraded.convert.ModernFiles.DATA_VERSION_1_12_2, com.useleess.litematicaupgraded.litematica.schematic.BaseSchematic.CURRENT_MINECRAFT_DATA_VERSION,
                "the game data version new schematics are saved with");
            Path file = ModernSchematicWriter.litematic(dir().resolve("versions.litematic"), ModernSchematicWriter.V1_21,
                list("minecraft:birch_planks"), new ModernSchematicWriter.Extras());
            CompoundData data = com.useleess.litematicaupgraded.malilib.util.data.tag.util.DataFileUtils.readCompoundDataFromNbtFile(file);
            eq(c, com.useleess.litematicaupgraded.convert.ModernFiles.DATA_VERSION_1_12_2, data.getInt("MinecraftDataVersion"), "the converted file's data version");
            c.check(data.getInt("Version") <= com.useleess.litematicaupgraded.litematica.schematic.LitematicaSchematic.CURRENT_SCHEMATIC_VERSION,
                "the converted file's format version " + data.getInt("Version") + " is one this Litematica warns about");
        });
        // a picture of the window as players see it (taken a few ticks after opening, once it has drawn)
        int[] shotTicks = {0};
        r.waitFor("convert/missing/window-screenshot", 200, c -> {
            shotTicks[0] = 0;
            Configs.Internal.CREATE_PLACEMENT_ON_LOAD.setBooleanValue(false);
            browserLoad(c, ModernSchematicWriter.litematic(dir().resolve("shot.litematic"), ModernSchematicWriter.V1_21, WITH_MISSING, new ModernSchematicWriter.Extras()));
        }, c -> {
            if (!(c.mc.currentScreen instanceof MissingBlocksScreen) || ++shotTicks[0] < 10) return false;
            Runner.shot(c.mc, "convert-missing-blocks-window");
            Configs.Internal.CREATE_PLACEMENT_ON_LOAD.setBooleanValue(true);
            SchematicHolder.INSTANCE.clearLoadedSchematics();
            MissingBlocks.choose(forget());
            Runner.dismissPopups(c.mc);
            return true;
        });
    }
}
