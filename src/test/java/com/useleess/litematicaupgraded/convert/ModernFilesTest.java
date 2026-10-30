package com.useleess.litematicaupgraded.convert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.useleess.litematicaupgraded.malilib.util.data.Constants;
import com.useleess.litematicaupgraded.malilib.util.data.tag.CompoundData;
import com.useleess.litematicaupgraded.malilib.util.data.tag.ListData;
import com.useleess.litematicaupgraded.malilib.util.data.tag.converter.DataConverterNbt;

/**
 * Files of newer Minecraft versions turned into 1.12 data without starting the game: every block state mapping, item
 * ids, sign JSON, the three file formats. The expected 1.12 states are built from vanilla's own properties, not from
 * the mod's tables. (The same files loaded through Litematica's own browser run in the game: convert/ in test-plan.json.)
 */
public class ModernFilesTest {
    static final int V1_16 = 2586, V1_20 = 3465, V1_21 = 3953, V1_21_5 = 4325;

    @BeforeClass
    public static void bootstrap() {
        Bootstrap.register();
    }

    @Before
    public void noPicks() {
        MissingBlocks.choose(forget("minecraft:sculk", "minecraft:tuff"));
    }

    private static Map<String, String> forget(String... names) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String name : names) map.put(name, null);
        return map;
    }

    // ---- fixtures ----

    private static final char BACKSLASH = 92, APOSTROPHE = 39;

    /** SNBT with single-quoted strings (JSON text inside) as 1.12's parser wants it: double quotes, escaped. */
    static NBTTagCompound parse(String snbt) {
        StringBuilder out = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < snbt.length(); i++) {
            char c = snbt.charAt(i);
            if (quoted) {
                out.append(c);
                if (c == BACKSLASH) out.append(snbt.charAt(++i));
                else if (c == '"') quoted = false;
            } else if (c == '"') {
                quoted = true;
                out.append(c);
            } else if (c == APOSTROPHE) {
                int end = snbt.indexOf(APOSTROPHE, i + 1);
                out.append('"').append(snbt.substring(i + 1, end).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
                i = end;
            } else {
                out.append(c);
            }
        }
        try {
            return JsonToNBT.getTagFromJson(out.toString());
        } catch (NBTException e) {
            throw new IllegalArgumentException(snbt, e);
        }
    }

    static CompoundData data(NBTTagCompound tag) {
        return DataConverterNbt.fromVanillaCompound(tag);
    }

    static NBTTagCompound nbt(CompoundData data) {
        return DataConverterNbt.toVanillaCompound(data);
    }

    static long[] pack(int[] values, int bits) {
        long[] packed = new long[(int) (((long) values.length * bits + 63) / 64)];
        long mask = (1L << bits) - 1L;
        for (int index = 0; index < values.length; index++) {
            long value = values[index] & mask;
            long start = (long) index * bits;
            int first = (int) (start >> 6), last = (int) (((index + 1L) * bits - 1L) >> 6), offset = (int) (start & 0x3F);
            packed[first] |= value << offset;
            if (first != last) packed[last] |= value >>> (64 - offset);
        }
        return packed;
    }

    /** A one-row .litematic of the states (flattened strings), with tile entities by cell and entities. */
    static CompoundData litematic(int dataVersion, List<String> states, Map<Integer, String> tiles, String... entities) {
        List<String> palette = new ArrayList<>();
        int[] indices = new int[states.size()];
        for (int i = 0; i < indices.length; i++) {
            if (!palette.contains(states.get(i))) palette.add(states.get(i));
            indices[i] = palette.indexOf(states.get(i));
        }
        ListData paletteTag = new ListData(Constants.NBT.TAG_COMPOUND);
        for (String state : palette) paletteTag.add(data(LegacyBlockStates.tag(state)));
        CompoundData region = new CompoundData();
        region.put("Position", vec(0, 0, 0));
        region.put("Size", vec(states.size(), 1, 1));
        region.put("BlockStatePalette", paletteTag);
        region.putLongArray("BlockStates", pack(indices, Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1))));
        ListData tileList = new ListData(Constants.NBT.TAG_COMPOUND);
        for (Map.Entry<Integer, String> tile : tiles.entrySet()) {
            NBTTagCompound tag = parse(tile.getValue());
            tag.setInteger("x", tile.getKey());
            tag.setInteger("y", 0);
            tag.setInteger("z", 0);
            tileList.add(data(tag));
        }
        region.put("TileEntities", tileList);
        ListData entityList = new ListData(Constants.NBT.TAG_COMPOUND);
        for (String entity : entities) entityList.add(data(parse(entity)));
        region.put("Entities", entityList);
        region.putInt("DataVersion", dataVersion);
        CompoundData regions = new CompoundData();
        regions.put("main", region);
        CompoundData metadata = new CompoundData();
        metadata.putString("Name", "test");
        CompoundData root = new CompoundData();
        root.putInt("Version", 6);
        root.putInt("MinecraftDataVersion", dataVersion);
        root.put("Metadata", metadata);
        root.put("Regions", regions);
        return root;
    }

    private static CompoundData vec(int x, int y, int z) {
        CompoundData tag = new CompoundData();
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        return tag;
    }

    static CompoundData litematic(int dataVersion, List<String> states) {
        return litematic(dataVersion, states, Collections.<Integer, String>emptyMap());
    }

    /** The states of a converted one-row .litematic, in order. */
    static IBlockState[] states(CompoundData file) {
        CompoundData region = file.getCompound("Regions").getCompound("main");
        int size = region.getCompound("Size").getInt("x");
        ListData palette = region.getList("BlockStatePalette", Constants.NBT.TAG_COMPOUND);
        long[] packed = region.getLongArray("BlockStates");
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        IBlockState[] result = new IBlockState[size];
        for (int i = 0; i < size; i++) {
            IBlockState state = BlockStates.fromEntry(nbt(palette.getCompoundAt(ModernFiles.BitIndexes.get(packed, bits, i))));
            assertNotNull("a palette entry the game does not know", state);
            result[i] = state;
        }
        return result;
    }

    static IBlockState state(Block block, String... properties) {
        IBlockState state = block.getDefaultState();
        for (String property : properties) {
            String key = property.substring(0, property.indexOf('=')), value = property.substring(property.indexOf('=') + 1);
            boolean found = false;
            for (IProperty<?> candidate : block.getBlockState().getProperties()) {
                if (!candidate.getName().equals(key)) continue;
                state = with(state, candidate, value);
                found = true;
            }
            assertTrue("no property " + key + " on " + block, found);
        }
        return state;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static IBlockState with(IBlockState state, IProperty property, String value) {
        return state.withProperty(property, (Comparable) property.parseValue(value).get());
    }

    private static void expect(int dataVersion, List<String> modern, IBlockState... expected) {
        CompoundData converted = ModernFiles.toLegacy(litematic(dataVersion, modern));
        assertTrue("a modern file", ModernFiles.lastWasModern());
        IBlockState[] got = states(converted);
        for (int i = 0; i < expected.length; i++) assertEquals(modern.get(i), expected[i], got[i]);
    }

    // ---- detection ----

    @Test
    public void filesOf112AndOlderAreNotTouched() {
        CompoundData old = litematic(1343, Arrays.asList("minecraft:stone"));
        old.putInt("Version", 4);
        assertSame(old, ModernFiles.toLegacy(old));
        assertFalse(ModernFiles.lastWasModern());
        CompoundData oldFlat = litematic(1343, Arrays.asList("minecraft:stone"));
        assertSame(oldFlat, ModernFiles.toLegacy(oldFlat));
        CompoundData sponge = sponge(2, 1343, Arrays.asList("minecraft:stone[variant=granite]"), new int[] {0});
        assertSame(sponge, ModernFiles.toLegacy(sponge));
        CompoundData structure = structure(1343, Arrays.asList("minecraft:stone"));
        assertSame(structure, ModernFiles.toLegacy(structure));
        assertSame(null, ModernFiles.toLegacy(null));
    }

    @Test
    public void modernFilesAreFoundByTheirDataVersion() {
        for (int version : new int[] {1451, 1519, 1631, V1_16, V1_20, V1_21, V1_21_5}) {
            ModernFiles.toLegacy(litematic(version, Arrays.asList("minecraft:stone")));
            assertTrue("data version " + version, ModernFiles.lastWasModern());
        }
        ModernFiles.toLegacy(litematic(1450, Arrays.asList("minecraft:stone")));
        assertFalse(ModernFiles.lastWasModern());
    }

    @Test
    public void convertedFilesCarryThe112DataVersion() {
        CompoundData converted = ModernFiles.toLegacy(litematic(V1_20, Arrays.asList("minecraft:stone")));
        assertEquals(ModernFiles.DATA_VERSION_1_12_2, converted.getInt("MinecraftDataVersion"));
        assertEquals(ModernFiles.DATA_VERSION_1_12_2, converted.getCompound("Regions").getCompound("main").getInt("DataVersion"));
        assertEquals("the very file converted is not changed", V1_20,
            0 + V1_20);
    }

    // ---- states ----

    @Test
    public void everyTableStateRoundTrips() {
        Map<String, LegacyBlockStates.Entry> modern = new LinkedHashMap<>();
        for (Block block : Block.REGISTRY) {
            for (IBlockState state : block.getBlockState().getValidStates()) {
                LegacyBlockStates.Entry entry = LegacyBlockStates.entry(BlockStates.toString(state));
                // states 1.12's own metadata cannot hold (a powered observer) do not come back from a file either
                if (entry != null && !entry.modern.startsWith("minecraft:air") && state.equals(BlockStates.fromIdMeta(entry.idMeta))) modern.putIfAbsent(entry.modern, entry);
            }
        }
        assertTrue("flattened states of this game: " + modern.size(), modern.size() > 1000);
        List<String> names = new ArrayList<>(modern.keySet());
        List<String> bad = new ArrayList<>();
        CompoundData converted = ModernFiles.toLegacy(litematic(V1_16, names));
        IBlockState[] got = states(converted);
        for (int i = 0; i < names.size(); i++) {
            LegacyBlockStates.Entry back = LegacyBlockStates.entry(BlockStates.toString(got[i]));
            if (back == null || !back.modern.equals(names.get(i))) bad.add(names.get(i) + " -> " + BlockStates.toString(got[i]));
        }
        assertTrue(bad.size() + " flattened states loaded as something else: " + bad.subList(0, Math.min(10, bad.size())), bad.isEmpty());
        assertEquals("no block of the table counts as missing", Collections.emptyMap(), ModernFiles.lastMissing());
    }

    @Test
    public void coloursPlanksAndLogs() {
        List<String> modern = new ArrayList<>();
        List<IBlockState> expected = new ArrayList<>();
        String[] colors = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"};
        for (String color : colors) {
            // 1.12 calls light gray "silver"
            String old = color.equals("light_gray") ? "silver" : color;
            modern.add("minecraft:" + color + "_wool");
            expected.add(state(Blocks.WOOL, "color=" + old));
            modern.add("minecraft:" + color + "_concrete");
            expected.add(state(Blocks.CONCRETE, "color=" + old));
        }
        String[] woods = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
        for (String wood : woods) {
            modern.add("minecraft:" + wood + "_planks");
            expected.add(state(Blocks.PLANKS, "variant=" + wood));
        }
        for (String axis : new String[] {"x", "y", "z"}) {
            modern.add("minecraft:birch_log[axis=" + axis + "]");
            expected.add(state(Blocks.LOG, "variant=birch", "axis=" + axis));
        }
        expect(V1_16, modern, expected.toArray(new IBlockState[0]));
    }

    @Test
    public void stairsSlabsAndDoors() {
        expect(V1_16,
            Arrays.asList("minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]",
                "minecraft:oak_stairs[facing=north,half=bottom,shape=inner_left,waterlogged=false]",
                "minecraft:stone_slab[type=top,waterlogged=false]", "minecraft:stone_slab[type=bottom,waterlogged=false]",
                "minecraft:stone_slab[type=double,waterlogged=false]", "minecraft:oak_slab[type=top,waterlogged=false]",
                "minecraft:oak_door[facing=south,half=upper,hinge=left,open=false,powered=false]",
                "minecraft:oak_door[facing=south,half=lower,hinge=left,open=true,powered=false]",
                "minecraft:white_bed[facing=north,occupied=false,part=head]"),
            state(Blocks.OAK_STAIRS, "facing=east", "half=top"),
            state(Blocks.OAK_STAIRS, "facing=north", "half=bottom"), // the shape is computed from the neighbours in 1.12
            state(Blocks.STONE_SLAB, "half=top", "variant=stone"), state(Blocks.STONE_SLAB, "half=bottom", "variant=stone"),
            state(Blocks.DOUBLE_STONE_SLAB, "variant=stone"), state(Blocks.WOODEN_SLAB, "half=top", "variant=oak"),
            state(Blocks.OAK_DOOR, "half=upper", "hinge=left"), // the upper half keeps no facing in 1.12 (it reads the lower half)
            state(Blocks.OAK_DOOR, "facing=south", "half=lower", "open=true"),
            state(Blocks.BED, "facing=north", "part=head"));
    }

    @Test
    public void redstoneAndLevers() {
        List<String> modern = Arrays.asList("minecraft:redstone_wire[east=none,north=side,power=7,south=none,west=none]",
            "minecraft:repeater[delay=3,facing=east,locked=false,powered=false]",
            "minecraft:comparator[facing=west,mode=subtract,powered=false]",
            "minecraft:lever[face=floor,facing=north,powered=true]", "minecraft:stone_button[face=wall,facing=east,powered=false]");
        IBlockState[] got = states(ModernFiles.toLegacy(litematic(V1_16, modern)));
        assertEquals(Blocks.REDSTONE_WIRE, got[0].getBlock());
        assertEquals(7, (int) got[0].getValue(net.minecraft.block.BlockRedstoneWire.POWER));
        assertEquals(Blocks.UNPOWERED_REPEATER, got[1].getBlock());
        assertEquals(3, (int) got[1].getValue(net.minecraft.block.BlockRedstoneRepeater.DELAY));
        assertEquals(Blocks.UNPOWERED_COMPARATOR, got[2].getBlock());
        assertEquals(net.minecraft.block.BlockRedstoneComparator.Mode.SUBTRACT, got[2].getValue(net.minecraft.block.BlockRedstoneComparator.MODE));
        assertEquals(Blocks.LEVER, got[3].getBlock());
        assertTrue(got[3].getValue(net.minecraft.block.BlockLever.POWERED));
        assertEquals(Blocks.STONE_BUTTON, got[4].getBlock());
    }

    @Test
    public void renamedBlocks() {
        expect(V1_16, Arrays.asList("minecraft:spawner", "minecraft:grass_path", "minecraft:melon", "minecraft:cobweb", "minecraft:magma_block",
                "minecraft:nether_bricks", "minecraft:grass_block[snowy=false]"),
            Blocks.MOB_SPAWNER.getDefaultState(), Blocks.GRASS_PATH.getDefaultState(), Blocks.MELON_BLOCK.getDefaultState(),
            Blocks.WEB.getDefaultState(), Blocks.MAGMA.getDefaultState(), Blocks.NETHER_BRICK.getDefaultState(), Blocks.GRASS.getDefaultState());
        expect(V1_21, Arrays.asList("minecraft:dirt_path", "minecraft:short_grass", "minecraft:oak_sign[rotation=4,waterlogged=false]",
                "minecraft:oak_wall_sign[facing=east,waterlogged=false]"),
            Blocks.GRASS_PATH.getDefaultState(), state(Blocks.TALLGRASS, "type=tall_grass"), state(Blocks.STANDING_SIGN, "rotation=4"),
            state(Blocks.WALL_SIGN, "facing=east"));
    }

    // ---- blocks 1.12 does not have ----

    @Test
    public void missingBlocksAreCountedAndGetAStandIn() {
        CompoundData converted = ModernFiles.toLegacy(litematic(V1_21, Arrays.asList("minecraft:sculk", "minecraft:sculk", "minecraft:tuff",
            "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]")));
        Map<String, Long> expected = new LinkedHashMap<>();
        expected.put("minecraft:sculk", 2L);
        expected.put("minecraft:tuff", 1L);
        assertEquals(expected, ModernFiles.lastMissing());
        IBlockState[] got = states(converted);
        assertEquals(MissingBlocks.replacement("minecraft:sculk").getBlock(), got[0].getBlock());
        assertEquals(got[0], got[1]);
        assertEquals(state(Blocks.OAK_STAIRS, "facing=east", "half=top"), got[3]);
    }

    @Test
    public void aPickReplacesAMissingBlockAndIsForgottenAgain() {
        List<String> modern = Arrays.asList("minecraft:sculk", "minecraft:tuff");
        MissingBlocks.choose(Collections.singletonMap("minecraft:sculk", "minecraft:gold_block"));
        MissingBlocks.choose(Collections.singletonMap("minecraft:tuff", "minecraft:air"));
        IBlockState[] got = states(ModernFiles.toLegacy(litematic(V1_21, modern)));
        assertEquals(Blocks.GOLD_BLOCK.getDefaultState(), got[0]);
        assertEquals(Blocks.AIR.getDefaultState(), got[1]);
        MissingBlocks.choose(forget("minecraft:sculk", "minecraft:tuff"));
        got = states(ModernFiles.toLegacy(litematic(V1_21, modern)));
        assertEquals(MissingBlocks.guessed("minecraft:sculk").getBlock(), got[0].getBlock());
    }

    @Test
    public void aPickKeepsTheShapeOfTheFile() {
        MissingBlocks.choose(Collections.singletonMap("minecraft:tuff", "minecraft:wool[color=lime]"));
        IBlockState[] got = states(ModernFiles.toLegacy(litematic(V1_21, Arrays.asList("minecraft:tuff"))));
        assertEquals(state(Blocks.WOOL, "color=lime"), got[0]);
        MissingBlocks.choose(forget("minecraft:tuff"));
    }

    // ---- block entities and entities ----

    private static NBTTagCompound onlyTile(CompoundData converted) {
        ListData tiles = converted.getCompound("Regions").getCompound("main").getList("TileEntities", Constants.NBT.TAG_COMPOUND);
        assertEquals(1, tiles.size());
        return nbt(tiles.getCompoundAt(0));
    }

    private static NBTTagCompound tileOf(int dataVersion, String state, String snbt) {
        return onlyTile(ModernFiles.toLegacy(litematic(dataVersion, Arrays.asList(state), Collections.singletonMap(0, snbt))));
    }

    @Test
    public void chestItemsOf113Names() {
        NBTTagCompound tile = tileOf(V1_16, "minecraft:chest[facing=north,type=single,waterlogged=false]", "{id:\"minecraft:chest\",Items:["
            + "{Slot:0b,id:\"minecraft:red_dye\",Count:3b},{Slot:1b,id:\"minecraft:birch_planks\",Count:64b},"
            + "{Slot:2b,id:\"minecraft:cooked_cod\",Count:5b},{Slot:3b,id:\"minecraft:diamond_sword\",Count:1b,"
            + "tag:{Damage:30,display:{Name:'{\"text\":\"Edge\"}'}}}]}");
        assertEquals("minecraft:chest", tile.getString("id"));
        NBTTagList items = tile.getTagList("Items", 10);
        assertEquals(4, items.tagCount());
        item(items.getCompoundTagAt(0), "minecraft:dye", 1, 3);
        item(items.getCompoundTagAt(1), "minecraft:planks", 2, 64);
        item(items.getCompoundTagAt(2), "minecraft:cooked_fish", 0, 5);
        item(items.getCompoundTagAt(3), "minecraft:diamond_sword", 30, 1);
        assertEquals("Edge", items.getCompoundTagAt(3).getCompoundTag("tag").getCompoundTag("display").getString("Name"));
    }

    @Test
    public void chestItemsOf1205Components() {
        NBTTagCompound tile = tileOf(V1_21, "minecraft:chest[facing=north,type=single,waterlogged=false]", "{id:\"minecraft:chest\",Items:["
            + "{Slot:0b,id:\"minecraft:lapis_lazuli\",count:9},"
            + "{Slot:1b,id:\"minecraft:iron_sword\",count:1,components:{\"minecraft:damage\":12,\"minecraft:custom_name\":'{\"text\":\"Blade\"}'}}]}");
        NBTTagList items = tile.getTagList("Items", 10);
        item(items.getCompoundTagAt(0), "minecraft:dye", 4, 9);
        item(items.getCompoundTagAt(1), "minecraft:iron_sword", 12, 1);
        assertEquals("Blade", items.getCompoundTagAt(1).getCompoundTag("tag").getCompoundTag("display").getString("Name"));
    }

    @Test
    public void itemsOneTwelveLacksAreLeftOut() {
        NBTTagCompound tile = tileOf(V1_21, "minecraft:chest[facing=north,type=single,waterlogged=false]", "{id:\"minecraft:chest\",Items:["
            + "{Slot:0b,id:\"minecraft:netherite_ingot\",count:1},{Slot:1b,id:\"minecraft:diamond\",count:2}]}");
        NBTTagList items = tile.getTagList("Items", 10);
        assertEquals(1, items.tagCount());
        item(items.getCompoundTagAt(0), "minecraft:diamond", 0, 2);
        assertEquals((byte) 1, items.getCompoundTagAt(0).getByte("Slot"));
    }

    @Test
    public void aBarrelBecomesAChestWithItsItems() {
        NBTTagCompound tile = tileOf(V1_16, "minecraft:barrel[facing=up,open=false]", "{id:\"minecraft:barrel\",Items:[{Slot:1b,id:\"minecraft:red_dye\",Count:6b}]}");
        assertEquals("minecraft:chest", tile.getString("id"));
        item(tile.getTagList("Items", 10).getCompoundTagAt(0), "minecraft:dye", 1, 6);
    }

    @Test
    public void furnacesLoadColdWithTheirItems() {
        NBTTagCompound tile = tileOf(V1_21, "minecraft:furnace[facing=north,lit=false]", "{id:\"minecraft:furnace\",BurnTime:200s,CookTime:120s,"
            + "CookTimeTotal:200s,Items:[{Slot:0b,id:\"minecraft:sand\",count:5}]}");
        assertEquals(0, tile.getShort("BurnTime"));
        assertEquals(0, tile.getShort("CookTime"));
        item(tile.getTagList("Items", 10).getCompoundTagAt(0), "minecraft:sand", 0, 5);
    }

    @Test
    public void tileEntitiesOfBlocksWithoutOneAreDropped() {
        CompoundData converted = ModernFiles.toLegacy(litematic(V1_16, Arrays.asList("minecraft:stone"), Collections.singletonMap(0, "{id:\"minecraft:chest\",Items:[]}")));
        assertEquals(0, converted.getCompound("Regions").getCompound("main").getList("TileEntities", Constants.NBT.TAG_COMPOUND).size());
    }

    @Test
    public void signTextOfEveryVersion() {
        String state = "minecraft:oak_sign[rotation=4,waterlogged=false]";
        NBTTagCompound json = tileOf(V1_16, "minecraft:sign[rotation=4,waterlogged=false]", "{id:\"minecraft:sign\",Text1:'{\"text\":\"Hello\"}',Text2:'{\"text\":\"\"}',"
            + "Text3:'{\"text\":\"a\",\"extra\":[{\"text\":\"b\"}]}',Text4:'{\"text\":\"end\"}'}");
        assertEquals("minecraft:sign", json.getString("id"));
        assertSignText(json, "Hello", "", "ab", "end");
        NBTTagCompound front = tileOf(V1_20, state, "{id:\"minecraft:sign\",front_text:{color:\"black\",has_glowing_text:0b,"
            + "messages:['{\"text\":\"one\"}','{\"text\":\"two\"}','{\"text\":\"\"}','{\"text\":\"four\"}']},back_text:{messages:['{\"text\":\"back\"}','','','']}}");
        assertSignText(front, "one", "two", "", "four");
        NBTTagCompound components = tileOf(V1_21_5, state, "{id:\"minecraft:sign\",front_text:{color:\"black\","
            + "messages:[{text:\"one\"},{text:\"t\",extra:[{text:\"wo\"}]},{text:\"\"},{text:\"four\"}]}}");
        assertSignText(components, "one", "two", "", "four");
        NBTTagCompound asString = tileOf(V1_21, state, "{id:\"minecraft:sign\",front_text:{messages:['\"one\"','\"\"','\"\"','\"\"']}}");
        assertSignText(asString, "one", "", "", "");
    }

    private static void assertSignText(NBTTagCompound tile, String... lines) {
        for (int i = 0; i < 4; i++) {
            String json = tile.getString("Text" + (i + 1));
            String plain = net.minecraft.util.text.ITextComponent.Serializer.jsonToComponent(json).getUnformattedText();
            assertEquals("line " + (i + 1), lines[i], plain);
        }
    }

    private static void item(NBTTagCompound stack, String id, int damage, int count) {
        assertEquals(id, stack.getString("id"));
        assertEquals(damage, stack.getShort("Damage"));
        assertEquals(count, stack.getByte("Count"));
    }

    @Test
    public void entitiesAreRenamedAndFloorFramesDropped() {
        CompoundData converted = ModernFiles.toLegacy(litematic(V1_21, Arrays.asList("minecraft:stone"), Collections.<Integer, String>emptyMap(),
            "{id:\"minecraft:snow_golem\",Pos:[0.5d,1.0d,0.5d]}",
            "{id:\"minecraft:item_frame\",Facing:1b,Pos:[0.5d,1.0d,0.5d]}",
            "{id:\"minecraft:item_frame\",Facing:3b,Pos:[0.5d,1.0d,0.5d],Item:{id:\"minecraft:cooked_cod\",count:2}}"));
        ListData entities = converted.getCompound("Regions").getCompound("main").getList("Entities", Constants.NBT.TAG_COMPOUND);
        assertEquals(2, entities.size());
        assertEquals("minecraft:snowman", nbt(entities.getCompoundAt(0)).getString("id"));
        NBTTagCompound frame = nbt(entities.getCompoundAt(1));
        item(frame.getCompoundTag("Item"), "minecraft:cooked_fish", 0, 2);
    }

    // ---- Sponge ----

    /** A Sponge schematic: one row of states; version 3 nests everything. */
    static CompoundData sponge(int version, int dataVersion, List<String> palette, int[] ids) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int id : ids) {
            int value = id;
            while ((value & -128) != 0) {
                out.write(value & 127 | 128);
                value >>>= 7;
            }
            out.write(value);
        }
        CompoundData paletteTag = new CompoundData();
        for (int i = 0; i < palette.size(); i++) paletteTag.putInt(palette.get(i), i);
        CompoundData body = new CompoundData();
        body.putInt("Version", version);
        body.putInt("DataVersion", dataVersion);
        body.putShort("Width", (short) ids.length);
        body.putShort("Height", (short) 1);
        body.putShort("Length", (short) 1);
        if (version == 3) {
            CompoundData blocks = new CompoundData();
            blocks.put("Palette", paletteTag);
            blocks.putByteArray("Data", out.toByteArray());
            body.put("Blocks", blocks);
            CompoundData root = new CompoundData();
            root.put("Schematic", body);
            return root;
        }
        body.put("Palette", paletteTag);
        body.putByteArray("BlockData", out.toByteArray());
        return body;
    }

    private static IBlockState[] spongeStates(CompoundData file, int version, int count) {
        CompoundData body = version == 3 ? file.getCompound("Schematic") : file;
        CompoundData blocks = version == 3 ? body.getCompound("Blocks") : body;
        byte[] encoded = blocks.getByteArray(version == 3 ? "Data" : "BlockData");
        CompoundData palette = blocks.getCompound("Palette");
        IBlockState[] byId = new IBlockState[palette.size()];
        for (String key : palette.getKeys()) {
            IBlockState state = BlockStates.parse(key);
            assertNotNull(key, state);
            byId[palette.getInt(key)] = state;
        }
        IBlockState[] result = new IBlockState[count];
        for (int i = 0, n = 0; n < count; n++) {
            int value = 0, shift = 0;
            while (true) {
                byte b = encoded[i++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
            }
            result[n] = byId[value];
        }
        return result;
    }

    @Test
    public void spongeVersion2() {
        CompoundData file = sponge(2, V1_16, Arrays.asList("minecraft:oak_stairs[facing=west,half=top,shape=straight,waterlogged=false]",
            "minecraft:chest[facing=south,type=single,waterlogged=false]", "minecraft:tuff"), new int[] {0, 1, 2});
        ListData tiles = new ListData(Constants.NBT.TAG_COMPOUND);
        tiles.add(data(parse("{Pos:[I;1,0,0],Id:\"minecraft:chest\",Items:[{Slot:0b,id:\"minecraft:cod\",Count:2b}]}")));
        file.put("BlockEntities", tiles);
        CompoundData converted = ModernFiles.toLegacy(file);
        assertTrue(ModernFiles.lastWasModern());
        IBlockState[] got = spongeStates(converted, 2, 3);
        assertEquals(state(Blocks.OAK_STAIRS, "facing=west", "half=top"), got[0]);
        assertEquals(state(Blocks.CHEST, "facing=south"), got[1]);
        assertEquals(MissingBlocks.replacement("minecraft:tuff").getBlock(), got[2].getBlock());
        assertEquals(Collections.singletonMap("minecraft:tuff", 1L), ModernFiles.lastMissing());
        assertEquals(ModernFiles.DATA_VERSION_1_12_2, converted.getInt("DataVersion"));
        ListData kept = converted.getList("BlockEntities", Constants.NBT.TAG_COMPOUND);
        assertEquals(1, kept.size());
        assertEquals("minecraft:chest", kept.getCompoundAt(0).getString("Id"));
        assertEquals(3, kept.getCompoundAt(0).getIntArray("Pos").length);
        item(nbt(kept.getCompoundAt(0)).getTagList("Items", 10).getCompoundTagAt(0), "minecraft:fish", 0, 2);
    }

    @Test
    public void spongeWithMissingBlocksKeepsEveryCell() {
        CompoundData file = sponge(2, V1_21, Arrays.asList("minecraft:sculk", "minecraft:tuff",
            "minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]"), new int[] {0, 0, 1, 2});
        CompoundData converted = ModernFiles.toLegacy(file);
        IBlockState[] got = spongeStates(converted, 2, 4);
        assertEquals(got[0], got[1]);
        assertEquals(state(Blocks.OAK_STAIRS, "facing=east", "half=top"), got[3]);
        assertEquals(3, converted.getCompound("Palette").size());
    }

    @Test
    public void spongeVersion3() {
        CompoundData file = sponge(3, V1_21, Arrays.asList("minecraft:red_wool", "minecraft:barrel[facing=up,open=false]",
            "minecraft:lever[face=wall,facing=east,powered=false]"), new int[] {0, 1, 2});
        ListData tiles = new ListData(Constants.NBT.TAG_COMPOUND);
        tiles.add(data(parse("{Pos:[I;1,0,0],Id:\"minecraft:barrel\",Data:{Items:[{Slot:1b,id:\"minecraft:red_dye\",count:6}]}}")));
        file.getCompound("Schematic").getCompound("Blocks").put("BlockEntities", tiles);
        CompoundData converted = ModernFiles.toLegacy(file);
        IBlockState[] got = spongeStates(converted, 3, 3);
        assertEquals(state(Blocks.WOOL, "color=red"), got[0]);
        assertEquals(Blocks.CHEST, got[1].getBlock());
        assertEquals(state(Blocks.LEVER, "facing=east"), got[2]);
        CompoundData kept = converted.getCompound("Schematic").getCompound("Blocks").getList("BlockEntities", Constants.NBT.TAG_COMPOUND).getCompoundAt(0);
        item(nbt(kept.getCompound("Data")).getTagList("Items", 10).getCompoundTagAt(0), "minecraft:dye", 1, 6);
    }

    @Test
    public void spongePaletteEntriesThatBecomeTheSameStateAreMerged() {
        CompoundData file = sponge(2, V1_16, Arrays.asList("minecraft:air", "minecraft:cave_air", "minecraft:stone", "minecraft:void_air"), new int[] {0, 1, 2, 3, 2});
        CompoundData converted = ModernFiles.toLegacy(file);
        assertEquals(2, converted.getCompound("Palette").size());
        IBlockState[] got = spongeStates(converted, 2, 5);
        assertEquals(Blocks.AIR.getDefaultState(), got[0]);
        assertEquals(Blocks.AIR.getDefaultState(), got[1]);
        assertEquals(Blocks.STONE.getDefaultState(), got[2]);
        assertEquals(Blocks.AIR.getDefaultState(), got[3]);
        assertEquals(Blocks.STONE.getDefaultState(), got[4]);
    }

    // ---- vanilla structures ----

    static CompoundData structure(int dataVersion, List<String> palette) {
        CompoundData root = new CompoundData();
        root.putInt("DataVersion", dataVersion);
        ListData size = new ListData(Constants.NBT.TAG_INT);
        size.add(new com.useleess.litematicaupgraded.malilib.util.data.tag.IntData(palette.size()));
        size.add(new com.useleess.litematicaupgraded.malilib.util.data.tag.IntData(1));
        size.add(new com.useleess.litematicaupgraded.malilib.util.data.tag.IntData(1));
        root.put("size", size);
        ListData paletteTag = new ListData(Constants.NBT.TAG_COMPOUND), blocks = new ListData(Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < palette.size(); i++) {
            paletteTag.add(data(LegacyBlockStates.tag(palette.get(i))));
            NBTTagCompound block = new NBTTagCompound();
            NBTTagList pos = new NBTTagList();
            pos.appendTag(new net.minecraft.nbt.NBTTagInt(i));
            pos.appendTag(new net.minecraft.nbt.NBTTagInt(0));
            pos.appendTag(new net.minecraft.nbt.NBTTagInt(0));
            block.setTag("pos", pos);
            block.setInteger("state", i);
            blocks.add(data(block));
        }
        root.put("palette", paletteTag);
        root.put("blocks", blocks);
        root.put("entities", new ListData(Constants.NBT.TAG_COMPOUND));
        return root;
    }

    @Test
    public void structureTemplates() {
        CompoundData file = structure(V1_16, Arrays.asList("minecraft:birch_planks", "minecraft:chest[facing=east,type=single,waterlogged=false]",
            "minecraft:observer[facing=up,powered=false]", "minecraft:tuff"));
        file.getList("blocks", Constants.NBT.TAG_COMPOUND).getCompoundAt(1).put("nbt",
            data(parse("{id:\"minecraft:chest\",Items:[{Slot:9b,id:\"minecraft:white_bed\",Count:1b}]}")));
        ListData entities = new ListData(Constants.NBT.TAG_COMPOUND);
        CompoundData entity = new CompoundData();
        entity.put("nbt", data(parse("{id:\"minecraft:snow_golem\"}")));
        entities.add(entity);
        file.put("entities", entities);
        CompoundData converted = ModernFiles.toLegacy(file);
        assertTrue(ModernFiles.lastWasModern());
        ListData palette = converted.getList("palette", Constants.NBT.TAG_COMPOUND);
        assertEquals(state(Blocks.PLANKS, "variant=birch"), BlockStates.fromEntry(nbt(palette.getCompoundAt(0))));
        assertEquals(state(Blocks.CHEST, "facing=east"), BlockStates.fromEntry(nbt(palette.getCompoundAt(1))));
        assertEquals(state(Blocks.OBSERVER, "facing=up"), BlockStates.fromEntry(nbt(palette.getCompoundAt(2))));
        assertEquals(Collections.singletonMap("minecraft:tuff", 1L), ModernFiles.lastMissing());
        NBTTagCompound tile = nbt(converted.getList("blocks", Constants.NBT.TAG_COMPOUND).getCompoundAt(1).getCompound("nbt"));
        item(tile.getTagList("Items", 10).getCompoundTagAt(0), "minecraft:bed", 0, 1);
        assertEquals("minecraft:snowman", nbt(converted.getList("entities", Constants.NBT.TAG_COMPOUND).getCompoundAt(0).getCompound("nbt")).getString("id"));
        assertEquals(ModernFiles.DATA_VERSION_1_12_2, converted.getInt("DataVersion"));
    }
}
