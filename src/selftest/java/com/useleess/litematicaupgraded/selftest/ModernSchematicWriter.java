package com.useleess.litematicaupgraded.selftest;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagInt;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;

import com.useleess.litematicaupgraded.convert.LegacyBlockStates;

/**
 * Writes small schematic files the way newer Minecraft versions save them: .litematic (flattened block names, any data
 * version), Sponge .schem versions 2 and 3, and vanilla structure .nbt. States are written as flattened strings
 * ({@code minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]}) in one row along x.
 */
final class ModernSchematicWriter {
    static final int V1_13 = 1519, V1_16 = 2586, V1_20 = 3465, V1_21 = 3953, V1_21_5 = 4325;
    private static final char BACKSLASH = 92, APOSTROPHE = 39;

    private ModernSchematicWriter() {}

    /** NBT from SNBT; single-quoted strings (JSON text inside) are accepted, as newer versions write them. */
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

    /** What goes into a file besides its blocks: block entities by cell, entities. */
    static final class Extras {
        final Map<Integer, NBTTagCompound> tiles = new LinkedHashMap<>();
        final List<NBTTagCompound> entities = new ArrayList<>();

        Extras tile(int cell, String snbt) {
            tiles.put(cell, parse(snbt));
            return this;
        }

        Extras entity(String snbt) {
            entities.add(parse(snbt));
            return this;
        }
    }

    private static NBTTagCompound vec(int x, int y, int z) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("x", x);
        tag.setInteger("y", y);
        tag.setInteger("z", z);
        return tag;
    }

    private static void save(Path file, NBTTagCompound root) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            CompressedStreamTools.writeCompressed(root, out);
        }
    }

    private static List<String> palette(List<String> states, int[] indices) {
        List<String> palette = new ArrayList<>();
        for (int i = 0; i < states.size(); i++) {
            if (!palette.contains(states.get(i))) palette.add(states.get(i));
            indices[i] = palette.indexOf(states.get(i));
        }
        return palette;
    }

    static Path litematic(Path file, int dataVersion, List<String> states, Extras extras) throws IOException {
        int[] indices = new int[states.size()];
        List<String> palette = palette(states, indices);
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        long[] packed = new long[(int) (((long) indices.length * bits + 63) / 64)];
        long mask = (1L << bits) - 1L;
        for (int index = 0; index < indices.length; index++) {
            long value = indices[index] & mask;
            long start = (long) index * bits;
            int first = (int) (start >> 6), last = (int) (((index + 1L) * bits - 1L) >> 6), offset = (int) (start & 0x3F);
            packed[first] |= value << offset;
            if (first != last) packed[last] |= value >>> (64 - offset);
        }
        NBTTagList paletteTag = new NBTTagList();
        for (String state : palette) paletteTag.appendTag(LegacyBlockStates.tag(state));
        NBTTagList tiles = new NBTTagList();
        for (Map.Entry<Integer, NBTTagCompound> tile : extras.tiles.entrySet()) {
            NBTTagCompound tag = tile.getValue().copy();
            tag.setInteger("x", tile.getKey());
            tag.setInteger("y", 0);
            tag.setInteger("z", 0);
            tiles.appendTag(tag);
        }
        NBTTagList entities = new NBTTagList();
        for (NBTTagCompound entity : extras.entities) entities.appendTag(entity.copy());
        NBTTagCompound region = new NBTTagCompound();
        region.setTag("Position", vec(0, 0, 0));
        region.setTag("Size", vec(states.size(), 1, 1));
        region.setTag("BlockStatePalette", paletteTag);
        region.setTag("BlockStates", new NBTTagLongArray(packed));
        region.setTag("TileEntities", tiles);
        region.setTag("Entities", entities);
        region.setTag("PendingBlockTicks", new NBTTagList());
        NBTTagCompound regions = new NBTTagCompound();
        regions.setTag("main", region);
        NBTTagCompound meta = new NBTTagCompound();
        meta.setString("Name", "modern test");
        meta.setString("Author", "selftest");
        meta.setString("Description", "");
        meta.setLong("TimeCreated", 0L);
        meta.setLong("TimeModified", 0L);
        meta.setTag("EnclosingSize", vec(states.size(), 1, 1));
        meta.setInteger("RegionCount", 1);
        meta.setInteger("TotalVolume", states.size());
        meta.setInteger("TotalBlocks", states.size());
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("Version", dataVersion >= V1_20 ? 6 : 5);
        root.setInteger("MinecraftDataVersion", dataVersion);
        root.setTag("Metadata", meta);
        root.setTag("Regions", regions);
        save(file, root);
        return file;
    }

    /** A Sponge schematic, version 2 or 3 (3 nests the body in "Schematic" and block data in "Blocks" / "Data"). */
    static Path sponge(Path file, int version, int dataVersion, List<String> states, Extras extras) throws IOException {
        int[] indices = new int[states.size()];
        List<String> palette = palette(states, indices);
        ByteArrayOutputStream blockData = new ByteArrayOutputStream();
        for (int id : indices) {
            int value = id;
            while ((value & -128) != 0) {
                blockData.write(value & 127 | 128);
                value >>>= 7;
            }
            blockData.write(value);
        }
        NBTTagCompound paletteTag = new NBTTagCompound();
        for (int i = 0; i < palette.size(); i++) paletteTag.setInteger(palette.get(i), i);
        NBTTagList tiles = new NBTTagList();
        for (Map.Entry<Integer, NBTTagCompound> tile : extras.tiles.entrySet()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setTag("Pos", new NBTTagIntArray(new int[] {tile.getKey(), 0, 0}));
            NBTTagCompound content = tile.getValue().copy();
            String id = content.getString("id");
            content.removeTag("id");
            entry.setString("Id", id);
            if (version == 3) {
                entry.setTag("Data", content);
            } else {
                for (String key : content.getKeySet()) entry.setTag(key, content.getTag(key));
            }
            tiles.appendTag(entry);
        }
        NBTTagList entities = new NBTTagList();
        for (NBTTagCompound entity : extras.entities) {
            NBTTagCompound content = entity.copy();
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("Id", content.getString("id"));
            content.removeTag("id");
            if (version == 3) {
                entry.setTag("Pos", content.getTag("Pos"));
                content.removeTag("Pos");
                entry.setTag("Data", content);
            } else {
                for (String key : content.getKeySet()) entry.setTag(key, content.getTag(key));
            }
            entities.appendTag(entry);
        }
        NBTTagCompound body = new NBTTagCompound();
        body.setInteger("Version", version);
        body.setInteger("DataVersion", dataVersion);
        body.setShort("Width", (short) states.size());
        body.setShort("Height", (short) 1);
        body.setShort("Length", (short) 1);
        body.setTag("Entities", entities);
        NBTTagCompound root = body;
        if (version == 3) {
            NBTTagCompound blocks = new NBTTagCompound();
            blocks.setTag("Palette", paletteTag);
            blocks.setTag("Data", new NBTTagByteArray(blockData.toByteArray()));
            blocks.setTag("BlockEntities", tiles);
            body.setTag("Blocks", blocks);
            root = new NBTTagCompound();
            root.setTag("Schematic", body);
        } else {
            body.setTag("Palette", paletteTag);
            body.setInteger("PaletteMax", palette.size());
            body.setTag("BlockData", new NBTTagByteArray(blockData.toByteArray()));
            body.setTag("BlockEntities", tiles);
        }
        save(file, root);
        return file;
    }

    /** A vanilla structure template (.nbt). */
    static Path structure(Path file, int dataVersion, List<String> states, Extras extras) throws IOException {
        int[] indices = new int[states.size()];
        List<String> palette = palette(states, indices);
        NBTTagList paletteTag = new NBTTagList();
        for (String state : palette) paletteTag.appendTag(LegacyBlockStates.tag(state));
        NBTTagList blocks = new NBTTagList();
        for (int i = 0; i < indices.length; i++) {
            NBTTagCompound block = new NBTTagCompound();
            NBTTagList pos = new NBTTagList();
            pos.appendTag(new NBTTagInt(i));
            pos.appendTag(new NBTTagInt(0));
            pos.appendTag(new NBTTagInt(0));
            block.setTag("pos", pos);
            block.setInteger("state", indices[i]);
            if (extras.tiles.containsKey(i)) block.setTag("nbt", extras.tiles.get(i).copy());
            blocks.appendTag(block);
        }
        NBTTagList entities = new NBTTagList();
        for (NBTTagCompound entity : extras.entities) {
            NBTTagCompound info = new NBTTagCompound();
            NBTTagList pos = new NBTTagList();
            NBTTagList source = entity.getTagList("Pos", 6);
            for (int i = 0; i < 3; i++) pos.appendTag(new NBTTagDouble(source.getDoubleAt(i)));
            info.setTag("pos", pos);
            info.setTag("nbt", entity.copy());
            entities.appendTag(info);
        }
        NBTTagList size = new NBTTagList();
        size.appendTag(new NBTTagInt(states.size()));
        size.appendTag(new NBTTagInt(1));
        size.appendTag(new NBTTagInt(1));
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("DataVersion", dataVersion);
        root.setTag("size", size);
        root.setTag("palette", paletteTag);
        root.setTag("blocks", blocks);
        root.setTag("entities", entities);
        save(file, root);
        return file;
    }
}
