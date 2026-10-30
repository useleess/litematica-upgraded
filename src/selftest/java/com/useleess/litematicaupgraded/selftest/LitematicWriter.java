package com.useleess.litematicaupgraded.selftest;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;
import net.minecraft.util.math.BlockPos;

/** Writes a small one-region .litematic (version 4, tight bit packing) from block states at positions that start at 0, 0, 0. */
final class LitematicWriter {
    private LitematicWriter() {}

    static void write(Path file, Map<BlockPos, IBlockState> blocks) throws IOException {
        write(file, blocks, new LinkedHashMap<>());
    }

    /** Also writes tile entity data (an id and the entity's own tags; the position is added from the map key). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void write(Path file, Map<BlockPos, IBlockState> blocks, Map<BlockPos, NBTTagCompound> tiles) throws IOException {
        int sx = 1, sy = 1, sz = 1;
        for (BlockPos p : blocks.keySet()) {
            sx = Math.max(sx, p.getX() + 1);
            sy = Math.max(sy, p.getY() + 1);
            sz = Math.max(sz, p.getZ() + 1);
        }
        List<IBlockState> palette = new ArrayList<>();
        palette.add(Blocks.AIR.getDefaultState());
        Map<IBlockState, Integer> index = new LinkedHashMap<>();
        index.put(palette.get(0), 0);
        int volume = sx * sy * sz;
        int[] ids = new int[volume];
        for (Map.Entry<BlockPos, IBlockState> e : blocks.entrySet()) {
            BlockPos p = e.getKey();
            Integer id = index.get(e.getValue());
            if (id == null) {
                id = palette.size();
                palette.add(e.getValue());
                index.put(e.getValue(), id);
            }
            ids[(p.getY() * sz + p.getZ()) * sx + p.getX()] = id;
        }
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        long[] packed = new long[(int) (((long) volume * bits + 63) / 64)];
        long mask = (1L << bits) - 1;
        for (int i = 0; i < volume; i++) {
            long start = (long) i * bits;
            int word = (int) (start >> 6), offset = (int) (start & 63);
            packed[word] |= ((long) ids[i] & mask) << offset;
            if (offset + bits > 64) packed[word + 1] |= ((long) ids[i] & mask) >>> (64 - offset);
        }
        assemble(file, sx, sy, sz, palette, packed, tiles, blocks.size());
    }

    /**
     * A big box of air with a few blocks in it: only the packed words of those cells are touched, so a 640 million cell
     * box costs the memory of its packed array (160 MB) and no per-cell arrays.
     */
    static void writeSparse(Path file, int sx, int sy, int sz, Map<BlockPos, IBlockState> blocks) throws IOException {
        List<IBlockState> palette = new ArrayList<>();
        palette.add(Blocks.AIR.getDefaultState());
        Map<IBlockState, Integer> index = new LinkedHashMap<>();
        index.put(palette.get(0), 0);
        for (IBlockState state : blocks.values()) {
            if (!index.containsKey(state)) {
                index.put(state, palette.size());
                palette.add(state);
            }
        }
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.size() - 1));
        long volume = (long) sx * sy * sz;
        long[] packed = new long[(int) ((volume * bits + 63) / 64)];
        long mask = (1L << bits) - 1;
        for (Map.Entry<BlockPos, IBlockState> e : blocks.entrySet()) {
            BlockPos p = e.getKey();
            long i = ((long) p.getY() * sz + p.getZ()) * sx + p.getX();
            long id = index.get(e.getValue());
            long start = i * bits;
            int word = (int) (start >> 6), offset = (int) (start & 63);
            packed[word] |= (id & mask) << offset;
            if (offset + bits > 64) packed[word + 1] |= (id & mask) >>> (64 - offset);
        }
        assemble(file, sx, sy, sz, palette, packed, new LinkedHashMap<>(), blocks.size());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void assemble(Path file, int sx, int sy, int sz, List<IBlockState> palette, long[] packed,
                                 Map<BlockPos, NBTTagCompound> tiles, int blockCount) throws IOException {
        long volume = (long) sx * sy * sz;
        NBTTagList paletteTag = new NBTTagList();
        for (IBlockState state : palette) {
            NBTTagCompound entry = new NBTTagCompound();
            Block block = state.getBlock();
            entry.setString("Name", Block.REGISTRY.getNameForObject(block).toString());
            if (!state.getPropertyKeys().isEmpty()) {
                NBTTagCompound props = new NBTTagCompound();
                for (IProperty property : state.getPropertyKeys()) {
                    props.setString(property.getName(), property.getName((Comparable) state.getValue(property)));
                }
                entry.setTag("Properties", props);
            }
            paletteTag.appendTag(entry);
        }
        NBTTagCompound region = new NBTTagCompound();
        region.setTag("Position", vec(0, 0, 0));
        region.setTag("Size", vec(sx, sy, sz));
        region.setTag("BlockStatePalette", paletteTag);
        region.setTag("BlockStates", new NBTTagLongArray(packed));
        NBTTagList tileList = new NBTTagList();
        for (Map.Entry<BlockPos, NBTTagCompound> e : tiles.entrySet()) {
            NBTTagCompound tile = e.getValue().copy();
            tile.setInteger("x", e.getKey().getX());
            tile.setInteger("y", e.getKey().getY());
            tile.setInteger("z", e.getKey().getZ());
            tileList.appendTag(tile);
        }
        region.setTag("TileEntities", tileList);
        region.setTag("Entities", new NBTTagList());
        region.setTag("PendingBlockTicks", new NBTTagList());
        NBTTagCompound regions = new NBTTagCompound();
        regions.setTag("main", region);
        NBTTagCompound meta = new NBTTagCompound();
        meta.setString("Name", "printer test");
        meta.setString("Author", "selftest");
        meta.setString("Description", "");
        meta.setLong("TimeCreated", 0L);
        meta.setLong("TimeModified", 0L);
        meta.setTag("EnclosingSize", vec(sx, sy, sz));
        meta.setInteger("RegionCount", 1);
        meta.setInteger("TotalVolume", (int) volume);
        meta.setInteger("TotalBlocks", blockCount);
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger("Version", 4);
        root.setInteger("MinecraftDataVersion", 1343);
        root.setTag("Metadata", meta);
        root.setTag("Regions", regions);
        try (OutputStream out = Files.newOutputStream(file)) {
            CompressedStreamTools.writeCompressed(root, out);
        }
    }

    private static NBTTagCompound vec(int x, int y, int z) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("x", x);
        tag.setInteger("y", y);
        tag.setInteger("z", z);
        return tag;
    }
}
