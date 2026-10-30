// Schematic files of newer Minecraft versions turned into the 1.12 form Litematica reads, after Schematica Upgraded
// (Lunatrius, HackerRouter, Useleess; MIT).
package com.useleess.litematicaupgraded.convert;

import com.useleess.litematicaupgraded.litematica.schematic.LitematicaSchematic;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.useleess.litematicaupgraded.malilib.util.data.Constants;
import com.useleess.litematicaupgraded.malilib.util.data.tag.CompoundData;
import com.useleess.litematicaupgraded.malilib.util.data.tag.ListData;
import com.useleess.litematicaupgraded.malilib.util.data.tag.converter.DataConverterNbt;

/**
 * Detects a schematic of a flattened (1.13+) Minecraft version by its data version and converts it, in the same file
 * format, to what 1.12 has: block states (palette entries), block entities and entities (items, sign text, names). The
 * result is read by Litematica's own readers like any 1.12 file. Files that are not modern are returned untouched.
 */
public final class ModernFiles {
    public static final int FLATTENING = 1451;
    public static final int DATA_VERSION_1_12_2 = 1343;
    private static final Logger LOGGER = LogManager.getLogger("litematica_upgraded");
    private static final ThreadLocal<Map<String, Long>> LAST_MISSING = ThreadLocal.withInitial(Collections::emptyMap);
    private static final ThreadLocal<Boolean> LAST_MODERN = ThreadLocal.withInitial(() -> false);

    private ModernFiles() {}

    /** The blocks this game does not have found by the last {@link #toLegacy} on this thread, with their counts. */
    public static Map<String, Long> lastMissing() { return LAST_MISSING.get(); }

    /** Whether the last {@link #toLegacy} on this thread found a modern file (and converted it). */
    public static boolean lastWasModern() { return LAST_MODERN.get(); }

    /** The 1.12 form of a file's data when it is from 1.13 or newer, else the data itself. Never throws. */
    public static CompoundData toLegacy(@Nullable CompoundData data) {
        LAST_MISSING.set(Collections.emptyMap());
        LAST_MODERN.set(false);
        if (data == null) return null;
        try {
            Kind kind = kind(data);
            if (kind == null) return data;
            LAST_MODERN.set(true);
            CompoundData copy = data.copy();
            ImportReport.begin();
            try {
                kind.convert(copy);
            } finally {
                ImportReport.end();
            }
            LAST_MISSING.set(new LinkedHashMap<>(ImportReport.lastCounts()));
            return copy;
        } catch (RuntimeException e) {
            LOGGER.error("Could not convert a schematic file from a newer Minecraft version", e);
            LAST_MODERN.set(false);
            return data;
        }
    }

    private enum Kind {
        LITEMATIC, SPONGE, STRUCTURE;

        void convert(CompoundData root) {
            switch (this) {
                case LITEMATIC: litematic(root); break;
                case SPONGE: sponge(root); break;
                default: structure(root); break;
            }
        }
    }

    /** The format of a file when it is a modern one, else null. */
    @Nullable
    static Kind kind(CompoundData root) {
        if (root.contains("Version", Constants.NBT.TAG_INT) && root.contains("Regions", Constants.NBT.TAG_COMPOUND)
            && root.contains("Metadata", Constants.NBT.TAG_COMPOUND)) {
            int version = root.getInt("Version"), dataVersion = root.getInt("MinecraftDataVersion");
            return version < 5 || dataVersion > 0 && dataVersion < FLATTENING ? null : Kind.LITEMATIC;
        }
        CompoundData body = root.contains("Schematic", Constants.NBT.TAG_COMPOUND) ? root.getCompound("Schematic") : root;
        if (body.contains("Version", Constants.NBT.TAG_INT) && body.contains("Width", Constants.NBT.TAG_ANY_NUMERIC)
            && (body.contains("Palette", Constants.NBT.TAG_COMPOUND) || body.contains("Blocks", Constants.NBT.TAG_COMPOUND))) {
            // Sponge files without a data version are 1.12 WorldEdit files, except version 3 (written since 1.13)
            if (!body.contains("DataVersion", Constants.NBT.TAG_INT)) return body.getInt("Version") >= 3 ? Kind.SPONGE : null;
            return body.getInt("DataVersion") < FLATTENING ? null : Kind.SPONGE;
        }
        if (root.contains("size", Constants.NBT.TAG_LIST) && root.contains("blocks", Constants.NBT.TAG_LIST)
            && (root.contains("palette", Constants.NBT.TAG_LIST) || root.contains("palettes", Constants.NBT.TAG_LIST))) {
            return root.getInt("DataVersion") < FLATTENING ? null : Kind.STRUCTURE;
        }
        return null;
    }

    // ---- palette entries ----

    /** A palette state, and for a block this game does not have the name of that block (null otherwise). */
    static final class PaletteState {
        final IBlockState state;
        /** The missing block this entry was read for (also when it became air), or null. */
        final String missing;

        PaletteState(IBlockState state, String missing) {
            this.state = state;
            this.missing = missing;
        }
    }

    static boolean isAirName(String name) {
        return name.equals("minecraft:air") || name.equals("minecraft:cave_air") || name.equals("minecraft:void_air");
    }

    static PaletteState paletteState(NBTTagCompound entry) {
        String name = entry.getString("Name");
        if (name.isEmpty()) {
            ImportReport.unknownBlock("(missing block name)", "minecraft:air");
            return new PaletteState(Blocks.AIR.getDefaultState(), null);
        }
        if (isAirName(name)) return new PaletteState(Blocks.AIR.getDefaultState(), null);
        IBlockState state = BlockStates.fromModern(entry);
        if (state != null) return new PaletteState(state, null);
        return missing(name, MissingBlocks.properties(entry));
    }

    /** A block this game does not have: what the player picked for it, or the closest block. */
    static PaletteState missing(String name, Map<String, String> properties) {
        IBlockState state = MissingBlocks.resolve(name, properties);
        ImportReport.unknownBlock(name, BlockStates.toString(state));
        return new PaletteState(state, name);
    }

    private static NBTTagCompound nbt(CompoundData data) {
        return DataConverterNbt.toVanillaCompound(data);
    }

    private static CompoundData data(NBTTagCompound tag) {
        return DataConverterNbt.fromVanillaCompound(tag);
    }

    private static void countMissing(PaletteState[] palette, long[] counts) {
        for (int i = 0; i < palette.length; i++) {
            if (palette[i] != null && palette[i].missing != null) ImportReport.missingBlocks(palette[i].missing, counts[i]);
        }
    }

    /** The tile entity as 1.12 stores it for the block it is placed at, or null when that block keeps none. */
    @Nullable
    private static NBTTagCompound tile(NBTTagCompound tag, IBlockState placed) {
        if (placed == null || !placed.getBlock().hasTileEntity(placed)) return null;
        return ModernNbt.tileTo112(tag, placed);
    }

    /** The entity as 1.12 stores it, or null when 1.12 cannot have it. */
    @Nullable
    private static NBTTagCompound entity(NBTTagCompound tag) {
        // hanging entities of 1.21.5+ keep their block as block_pos, older ones as TileX / TileY / TileZ
        if (!tag.hasKey("TileX") && tag.hasKey("block_pos")) {
            int[] anchor = tag.getIntArray("block_pos");
            if (anchor.length == 3) {
                tag.setInteger("TileX", anchor[0]);
                tag.setInteger("TileY", anchor[1]);
                tag.setInteger("TileZ", anchor[2]);
                tag.removeTag("block_pos");
            }
        }
        return ModernNbt.entityTo112(tag);
    }

    // ---- .litematic ----

    private static void litematic(CompoundData root) {
        CompoundData regions = root.getCompound("Regions");
        for (String name : regions.getKeys()) region(regions.getCompound(name));
        root.putInt("MinecraftDataVersion", DATA_VERSION_1_12_2);
        // converted to what this Litematica reads: no "unknown (future?) schematic version" warning for it
        if (root.getInt("Version") > LitematicaSchematic.CURRENT_SCHEMATIC_VERSION) root.putInt("Version", LitematicaSchematic.CURRENT_SCHEMATIC_VERSION);
    }

    private static void region(CompoundData region) {
        CompoundData size = region.getCompound("Size");
        int width = Math.abs(size.getInt("x")), height = Math.abs(size.getInt("y")), length = Math.abs(size.getInt("z"));
        ListData paletteTag = region.getList("BlockStatePalette", Constants.NBT.TAG_COMPOUND);
        PaletteState[] palette = new PaletteState[paletteTag.size()];
        ListData converted = new ListData(Constants.NBT.TAG_COMPOUND);
        boolean anyMissing = false;
        for (int i = 0; i < palette.length; i++) {
            palette[i] = paletteState(nbt(paletteTag.getCompoundAt(i)));
            converted.add(data(BlockStates.toEntry(palette[i].state)));
            anyMissing |= palette[i].missing != null;
        }
        long[] states = region.getLongArray("BlockStates");
        int bits = Math.max(2, Integer.SIZE - Integer.numberOfLeadingZeros(palette.length - 1));
        long volume = (long) width * height * length;
        if (anyMissing) {
            long[] counts = new long[palette.length];
            for (long i = 0; i < volume; i++) {
                int index = BitIndexes.get(states, bits, i);
                if (index >= 0 && index < counts.length) counts[index]++;
            }
            countMissing(palette, counts);
        }

        ListData tiles = region.getList("TileEntities", Constants.NBT.TAG_COMPOUND), keptTiles = new ListData(Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tiles.size(); i++) {
            CompoundData source = tiles.getCompoundAt(i);
            int x = source.getInt("x"), y = source.getInt("y"), z = source.getInt("z");
            if (x < 0 || y < 0 || z < 0 || x >= width || y >= height || z >= length) continue;
            int index = BitIndexes.get(states, bits, x + (long) width * (z + (long) length * y));
            if (index < 0 || index >= palette.length) continue;
            NBTTagCompound tile = tile(nbt(source), palette[index].state);
            if (tile != null) keptTiles.add(data(tile));
        }
        ListData entities = region.getList("Entities", Constants.NBT.TAG_COMPOUND), keptEntities = new ListData(Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) {
            NBTTagCompound entity = entity(nbt(entities.getCompoundAt(i)));
            if (entity != null) keptEntities.add(data(entity));
        }
        region.put("BlockStatePalette", converted);
        region.put("TileEntities", keptTiles);
        region.put("Entities", keptEntities);
        if (region.contains("DataVersion", Constants.NBT.TAG_INT)) region.putInt("DataVersion", DATA_VERSION_1_12_2);
    }

    // ---- Sponge .schem (versions 1 to 3) ----

    private static void sponge(CompoundData root) {
        boolean nested = root.contains("Schematic", Constants.NBT.TAG_COMPOUND);
        CompoundData body = nested ? root.getCompound("Schematic") : root;
        int version = body.getInt("Version");
        int width = body.getShort("Width") & 0xFFFF, height = body.getShort("Height") & 0xFFFF, length = body.getShort("Length") & 0xFFFF;
        CompoundData blocks = version == 3 ? body.getCompound("Blocks") : body;
        String dataKey = version == 3 ? "Data" : "BlockData";
        CompoundData paletteTag = blocks.getCompound("Palette");

        int max = 0;
        for (String key : paletteTag.getKeys()) max = Math.max(max, paletteTag.getInt(key) + 1);
        if (max > 1 << 20) throw new IllegalArgumentException("Sponge palette too large");
        PaletteState[] byId = new PaletteState[max];
        int[] remap = new int[max];
        Map<String, Integer> newIds = new LinkedHashMap<>();
        List<IBlockState> newStates = new ArrayList<>();
        for (String key : paletteTag.getKeys()) {
            int id = paletteTag.getInt(key);
            if (id < 0) throw new IllegalArgumentException("Invalid Sponge palette index");
            PaletteState state;
            if (isAirName(key.contains("[") ? key.substring(0, key.indexOf('[')) : key)) {
                state = new PaletteState(Blocks.AIR.getDefaultState(), null);
            } else {
                IBlockState known = BlockStates.fromStateString(key);
                if (known != null) {
                    state = new PaletteState(known, null);
                } else {
                    NBTTagCompound entry = LegacyBlockStates.tag(key);
                    state = missing(entry.getString("Name"), MissingBlocks.properties(entry));
                }
            }
            byId[id] = state;
            String newKey = BlockStates.toString(state.state);
            Integer index = newIds.get(newKey);
            if (index == null) {
                index = newIds.size();
                newIds.put(newKey, index);
                newStates.add(state.state);
            }
            remap[id] = index;
        }

        byte[] encoded = blocks.getByteArray(dataKey);
        long volume = (long) width * height * length;
        int[] ids = new int[(int) Math.min(volume, Integer.MAX_VALUE - 8)];
        int count = 0;
        long[] missingCounts = new long[max];
        for (int i = 0; i < encoded.length && count < ids.length; count++) {
            int value = 0, shift = 0;
            while (true) {
                if (i >= encoded.length) throw new IllegalArgumentException("Truncated Sponge block data");
                byte b = encoded[i++];
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) break;
                shift += 7;
                if (shift > 28) throw new IllegalArgumentException("Invalid Sponge block data");
            }
            if (value < 0 || value >= max || byId[value] == null) throw new IllegalArgumentException("Invalid Sponge palette entry");
            if (byId[value].missing != null) missingCounts[value]++;
            ids[count] = value;
        }
        countMissing(byId, missingCounts);

        ByteArrayOutputStream out = new ByteArrayOutputStream(encoded.length);
        for (int i = 0; i < count; i++) {
            int value = remap[ids[i]];
            while ((value & -128) != 0) {
                out.write(value & 127 | 128);
                value >>>= 7;
            }
            out.write(value);
        }
        blocks.putByteArray(dataKey, out.toByteArray());
        CompoundData newPalette = new CompoundData();
        for (Map.Entry<String, Integer> entry : newIds.entrySet()) newPalette.putInt(entry.getKey(), entry.getValue());
        blocks.put("Palette", newPalette);
        if (blocks.contains("PaletteMax", Constants.NBT.TAG_ANY_NUMERIC)) blocks.putInt("PaletteMax", newIds.size());

        String tileKey = version == 1 ? "TileEntities" : "BlockEntities";
        ListData tiles = blocks.getList(tileKey, Constants.NBT.TAG_COMPOUND), keptTiles = new ListData(Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < tiles.size(); i++) {
            CompoundData entry = tiles.getCompoundAt(i);
            int[] pos = entry.getIntArray("Pos");
            if (pos.length != 3 || pos[0] < 0 || pos[1] < 0 || pos[2] < 0 || pos[0] >= width || pos[1] >= height || pos[2] >= length) continue;
            int at = pos[0] + width * (pos[2] + length * pos[1]);
            if (at >= count) continue;
            IBlockState placed = newStates.get(remap[ids[at]]);
            NBTTagCompound tag = version == 3 ? nbt(entry.getCompound("Data")) : nbt(entry);
            tag.removeTag("Pos");
            tag.removeTag("Id");
            tag.setString("id", entry.getString("Id").isEmpty() ? tag.getString("id") : entry.getString("Id"));
            NBTTagCompound tile = tile(tag, placed);
            if (tile == null) continue;
            String id = tile.getString("id");
            CompoundData out2 = new CompoundData();
            CompoundData content = data(tile);
            if (version == 3) {
                out2.putString("Id", id);
                out2.putIntArray("Pos", pos);
                out2.put("Data", content);
            } else {
                out2 = content;
                out2.remove("id");
                out2.putString("Id", id);
                out2.putIntArray("Pos", pos);
            }
            keptTiles.add(out2);
        }
        blocks.put(tileKey, keptTiles);

        ListData entities = body.getList("Entities", Constants.NBT.TAG_COMPOUND), keptEntities = new ListData(Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) {
            CompoundData entry = entities.getCompoundAt(i);
            NBTTagCompound tag = version == 3 ? nbt(entry.getCompound("Data")) : nbt(entry);
            tag.removeTag("Id");
            tag.setString("id", entry.getString("Id").isEmpty() ? tag.getString("id") : entry.getString("Id"));
            NBTTagCompound converted = entity(tag);
            if (converted == null) continue;
            String id = converted.getString("id");
            CompoundData content = data(converted);
            if (version == 3) {
                CompoundData wrapped = new CompoundData();
                wrapped.putString("Id", id);
                if (entry.contains("Pos", Constants.NBT.TAG_LIST)) wrapped.put("Pos", entry.getList("Pos", Constants.NBT.TAG_DOUBLE));
                content.remove("Pos");
                content.putString("id", id);
                wrapped.put("Data", content);
                keptEntities.add(wrapped);
            } else {
                content.remove("id");
                content.putString("Id", id);
                keptEntities.add(content);
            }
        }
        body.put("Entities", keptEntities);
        body.putInt("DataVersion", DATA_VERSION_1_12_2);
    }

    // ---- vanilla structure .nbt ----

    private static void structure(CompoundData root) {
        ListData paletteTag;
        if (root.contains("palette", Constants.NBT.TAG_LIST)) {
            paletteTag = root.getList("palette", Constants.NBT.TAG_COMPOUND);
        } else {
            // structures with random variants (shipwrecks) keep several palettes; the first one is used
            ListData palettes = root.getList("palettes", Constants.NBT.TAG_LIST);
            if (palettes.size() == 0) throw new IllegalArgumentException("Structure without a palette");
            paletteTag = (ListData) palettes.get(0);
            root.remove("palettes");
        }
        PaletteState[] palette = new PaletteState[paletteTag.size()];
        ListData converted = new ListData(Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < palette.length; i++) {
            palette[i] = paletteState(nbt(paletteTag.getCompoundAt(i)));
            converted.add(data(BlockStates.toEntry(palette[i].state)));
        }
        long[] counts = new long[palette.length];
        ListData blocks = root.getList("blocks", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++) {
            CompoundData block = blocks.getCompoundAt(i);
            int state = block.getInt("state");
            if (state < 0 || state >= palette.length) continue;
            counts[state]++;
            if (block.contains("nbt", Constants.NBT.TAG_COMPOUND)) {
                NBTTagCompound tile = tile(nbt(block.getCompound("nbt")), palette[state].state);
                if (tile == null) block.remove("nbt");
                else block.put("nbt", data(tile));
            }
        }
        countMissing(palette, counts);
        ListData entities = root.getList("entities", Constants.NBT.TAG_COMPOUND), kept = new ListData(Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) {
            CompoundData info = entities.getCompoundAt(i);
            NBTTagCompound tag = nbt(info.getCompound("nbt"));
            NBTTagCompound entity = entity(tag);
            if (entity == null) continue;
            info.put("nbt", data(entity));
            kept.add(info);
        }
        root.put("palette", converted);
        root.put("entities", kept);
        root.putInt("DataVersion", DATA_VERSION_1_12_2);
    }

    /** Palette indices of Litematica's tightly packed long arrays (entries may cross long boundaries). */
    static final class BitIndexes {
        private BitIndexes() {}

        static int get(long[] data, int bits, long index) {
            long start = index * bits;
            int first = (int) (start >> 6), last = (int) (((index + 1L) * bits - 1L) >> 6), offset = (int) (start & 0x3F);
            long mask = (1L << bits) - 1L;
            if (first >= data.length) return 0;
            if (first == last) return (int) (data[first] >>> offset & mask);
            if (last >= data.length) return 0;
            return (int) ((data[first] >>> offset | data[last] << (64 - offset)) & mask);
        }
    }
}
