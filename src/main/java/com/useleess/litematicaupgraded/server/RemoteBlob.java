// The data of a server-side save / paste / edit: block regions with their block entities and entities, as gzip NBT.
package com.useleess.litematicaupgraded.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

/**
 * One region is a box of blocks: a palette of state strings ({@link StateStrings}), an index per block position (x fastest,
 * then z, then y; -1 = nothing here) and the block entity / entity tags of the box. Positions are absolute world positions.
 */
public final class RemoteBlob {
    public static final String OP_FILL = "fill", OP_PASTE = "paste";
    private static final long MAX_NBT_BYTES = 512L * 1024 * 1024;

    private RemoteBlob() {}

    public static final class Region {
        public String name = "";
        public int x, y, z, sx, sy, sz;
        public final List<String> palette = new ArrayList<>();
        public int[] indices = new int[0];
        public final List<NBTTagCompound> tiles = new ArrayList<>();
        public final List<NBTTagCompound> entities = new ArrayList<>();
        private final Map<String, Integer> paletteIndex = new HashMap<>();

        public Region() {}

        public Region(String name, int x, int y, int z, int sx, int sy, int sz) {
            this.name = name;
            this.x = x; this.y = y; this.z = z;
            this.sx = sx; this.sy = sy; this.sz = sz;
            this.indices = new int[sx * sy * sz];
            Arrays.fill(indices, -1);
        }

        public long volume() { return (long) sx * sy * sz; }

        public int offset(int worldX, int worldY, int worldZ) {
            return ((worldY - y) * sz + (worldZ - z)) * sx + (worldX - x);
        }

        /** Sets the state of a position inside the box. */
        public void set(int worldX, int worldY, int worldZ, String state) {
            Integer id = paletteIndex.get(state);
            if (id == null) {
                id = palette.size();
                palette.add(state);
                paletteIndex.put(state, id);
            }
            indices[offset(worldX, worldY, worldZ)] = id;
        }

        public NBTTagCompound toNbt() {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("name", name);
            tag.setIntArray("box", new int[] {x, y, z, sx, sy, sz});
            NBTTagList states = new NBTTagList();
            for (String state : palette) states.appendTag(new NBTTagString(state));
            tag.setTag("palette", states);
            tag.setIntArray("indices", indices);
            tag.setTag("tiles", list(tiles));
            tag.setTag("entities", list(entities));
            return tag;
        }

        /** Reads a region; a malformed one throws IllegalArgumentException. */
        public static Region fromNbt(NBTTagCompound tag) {
            int[] box = tag.getIntArray("box");
            if (box.length != 6 || box[3] < 1 || box[4] < 1 || box[5] < 1 || (long) box[3] * box[4] * box[5] > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("bad region box");
            }
            Region region = new Region();
            region.name = tag.getString("name");
            region.x = box[0]; region.y = box[1]; region.z = box[2];
            region.sx = box[3]; region.sy = box[4]; region.sz = box[5];
            NBTTagList states = tag.getTagList("palette", 8);
            for (int i = 0; i < states.tagCount(); i++) region.palette.add(states.getStringTagAt(i));
            region.indices = tag.getIntArray("indices");
            if (region.indices.length != region.volume()) throw new IllegalArgumentException("bad region size");
            for (int id : region.indices) {
                if (id < -1 || id >= region.palette.size()) throw new IllegalArgumentException("bad palette index");
            }
            readList(tag.getTagList("tiles", 10), region.tiles);
            readList(tag.getTagList("entities", 10), region.entities);
            return region;
        }
    }

    private static NBTTagList list(List<NBTTagCompound> tags) {
        NBTTagList list = new NBTTagList();
        for (NBTTagCompound tag : tags) list.appendTag(tag);
        return list;
    }

    private static void readList(NBTTagList list, List<NBTTagCompound> out) {
        for (int i = 0; i < list.tagCount(); i++) out.add(list.getCompoundTagAt(i));
    }

    public static NBTTagList regionList(List<Region> regions) {
        NBTTagList list = new NBTTagList();
        for (Region region : regions) list.appendTag(region.toNbt());
        return list;
    }

    public static List<Region> regions(NBTTagCompound blob) {
        List<Region> regions = new ArrayList<>();
        NBTTagList list = blob.getTagList("regions", 10);
        for (int i = 0; i < list.tagCount(); i++) regions.add(Region.fromNbt(list.getCompoundTagAt(i)));
        return regions;
    }

    public static byte[] gzip(NBTTagCompound tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            CompressedStreamTools.write(tag, out);
        }
        return bytes.toByteArray();
    }

    /** Reads gzip NBT with a size limit, so a small upload cannot expand without bound. */
    public static NBTTagCompound read(byte[] compressed) throws IOException {
        try (DataInputStream in = new DataInputStream(new GZIPInputStream(new ByteArrayInputStream(compressed)))) {
            return CompressedStreamTools.read(in, new NBTSizeTracker(MAX_NBT_BYTES));
        }
    }
}
