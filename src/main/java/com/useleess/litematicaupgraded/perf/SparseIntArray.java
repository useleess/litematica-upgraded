package com.useleess.litematicaupgraded.perf;

import com.useleess.litematicaupgraded.litematica.schematic.container.PackedIntArray;
import com.useleess.litematicaupgraded.litematica.schematic.container.TightLongBackedIntArray;

/**
 * Block ids of a box in 16 x 16 x 16 sections that only exist where something other than id 0 (air) is stored, so a huge,
 * mostly empty box takes memory only for the sections that hold blocks (the idea of Schematica Upgraded's sparse storage,
 * MIT: Lunatrius / HackerRouter / Useleess). The entries are numbered like the dense packed array (x fastest, then z, then y).
 * Section tables are plain arrays indexed by section, so a lookup is a few divisions and two array reads.
 */
public final class SparseIntArray implements PackedIntArray {
    public static final int MAX_BITS = 16;

    private final int bits;
    private final long size;
    private final int sizeX, sizeY, sizeZ;
    private final long layer;
    private final int sectionsX, sectionsY, sectionsZ;
    private final char[][] sections;
    private int allocated;

    public SparseIntArray(int bits, int sizeX, int sizeY, int sizeZ) {
        if (bits < 1 || bits > MAX_BITS) throw new IllegalArgumentException("bits " + bits);
        this.bits = bits;
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.size = (long) sizeX * sizeY * sizeZ;
        this.layer = (long) sizeX * sizeZ;
        this.sectionsX = (sizeX + 15) >> 4;
        this.sectionsY = (sizeY + 15) >> 4;
        this.sectionsZ = (sizeZ + 15) >> 4;
        long count = (long) sectionsX * sectionsY * sectionsZ;
        if (count > Integer.MAX_VALUE - 8) throw new IllegalArgumentException("too many sections: " + count);
        this.sections = new char[(int) count][];
    }

    /**
     * Reads a tightly packed long array (as the .litematic file holds it) into sparse sections. Words that are zero are
     * not looked at (all their entries are id 0), so the work follows the number of blocks, not the size of the box.
     * Returns null when the array does not fit the box or the entries are wider than {@link #MAX_BITS}.
     */
    public static SparseIntArray fromPacked(int bits, int sizeX, int sizeY, int sizeZ, long[] words) {
        long volume = (long) sizeX * sizeY * sizeZ;
        if (words == null || bits < 1 || bits > MAX_BITS || (volume * bits + 63) / 64 > words.length) return null;
        SparseIntArray out;
        try {
            out = new SparseIntArray(bits, sizeX, sizeY, sizeZ);
        } catch (IllegalArgumentException e) {
            return null;
        }
        TightLongBackedIntArray tight = new TightLongBackedIntArray(bits, volume, words);
        for (int w = 0; w < words.length; w++) {
            if (words[w] == 0L) continue;
            long first = (w * 64L) / bits;
            long last = Math.min(volume - 1, (w * 64L + 63) / bits);
            for (long e = first; e <= last; e++) {
                int value = tight.getAt(e);
                if (value != 0) out.setAt(e, value);
            }
        }
        return out;
    }

    /** Whether the packed array has so few non-zero words that sparse sections will save memory. */
    public static boolean worthIt(long[] words) {
        if (words == null || words.length < 4096) return false;
        long nonZero = 0;
        for (long w : words) if (w != 0L) nonZero++;
        return nonZero * 8 < words.length; // under an eighth of the words hold anything
    }

    public int allocatedSections() {
        return allocated;
    }

    public int totalSections() {
        return sections.length;
    }

    /** Whether the 16-high slice of sections at this section height holds any block (for tests and for skipping). */
    public boolean sliceHasBlocks(int sectionY) {
        if (sectionY < 0 || sectionY >= sectionsY) return false;
        for (int sz = 0; sz < sectionsZ; sz++) {
            for (int sx = 0; sx < sectionsX; sx++) {
                if (sections[sectionIndex(sx, sectionY, sz)] != null) return true;
            }
        }
        return false;
    }

    private int sectionIndex(int sx, int sy, int sz) {
        return (sy * sectionsZ + sz) * sectionsX + sx;
    }

    @Override
    public long size() {
        return size;
    }

    @Override
    public int getEntryBitWidth() {
        return bits;
    }

    @Override
    public int getAt(long index) {
        if (index < 0 || index >= size) return 0;
        int y = (int) (index / layer);
        int rest = (int) (index - (long) y * layer);
        int z = rest / sizeX;
        int x = rest - z * sizeX;
        char[] section = sections[sectionIndex(x >> 4, y >> 4, z >> 4)];
        return section == null ? 0 : section[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)];
    }

    @Override
    public void setAt(long index, int value) {
        if (index < 0 || index >= size) return;
        int y = (int) (index / layer);
        int rest = (int) (index - (long) y * layer);
        int z = rest / sizeX;
        int x = rest - z * sizeX;
        int si = sectionIndex(x >> 4, y >> 4, z >> 4);
        char[] section = sections[si];
        if (section == null) {
            if (value == 0) return;
            sections[si] = section = new char[4096];
            allocated++;
        }
        section[((y & 15) << 8) | ((z & 15) << 4) | (x & 15)] = (char) (value & 0xFFFF);
    }

    @Override
    public long[] getValueCounts() {
        long[] counts = new long[1 << bits];
        long nonZero = 0;
        for (char[] section : sections) {
            if (section == null) continue;
            for (char id : section) {
                if (id != 0) {
                    counts[id]++;
                    nonZero++;
                }
            }
        }
        // cells outside the box in an edge section are never set, so they do not count as air either
        counts[0] = size - nonZero;
        return counts;
    }

    @Override
    public SparseIntArray copy() {
        SparseIntArray copy = new SparseIntArray(bits, sizeX, sizeY, sizeZ);
        for (int i = 0; i < sections.length; i++) {
            if (sections[i] != null) copy.sections[i] = sections[i].clone();
        }
        copy.allocated = allocated;
        return copy;
    }

    @Override
    public PackedIntArray createNewArray(int bitsPerEntry, long arraySize) {
        return new SparseIntArray(Math.min(MAX_BITS, Math.max(1, bitsPerEntry)), sizeX, sizeY, sizeZ);
    }
}
