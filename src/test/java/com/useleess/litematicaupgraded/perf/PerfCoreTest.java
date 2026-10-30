package com.useleess.litematicaupgraded.perf;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import org.junit.Test;

import com.useleess.litematicaupgraded.litematica.schematic.container.TightLongBackedIntArray;

public class PerfCoreTest {
    @Test public void sectionSlicesMergeNeighboursAndCountSkipped() {
        long scannedBefore = PerfStats.slicesScanned(), skippedBefore = PerfStats.slicesSkipped();
        // sections 0..5 (y 0..95): 1, 2 and 4 are needed
        int[] ranges = SectionSlices.neededRanges(0, 95, s -> s == 1 || s == 2 || s == 4);
        assertArrayEquals(new int[] {16, 47, 64, 79}, ranges);
        assertEquals(6, PerfStats.slicesScanned() - scannedBefore);
        assertEquals(3, PerfStats.slicesSkipped() - skippedBefore);
        // a range that does not start on a section border is clamped
        assertArrayEquals(new int[] {5, 20}, SectionSlices.neededRanges(5, 20, s -> true));
        assertEquals(0, SectionSlices.neededRanges(5, 20, s -> false).length);
        assertEquals(0, SectionSlices.neededRanges(9, 3, s -> true).length);
    }

    @Test public void cellIteratorKeepsPlainLoopOrderInsideRanges() {
        int[] ranges = {1, 2, 5, 5};
        BoxCellIterator it = new BoxCellIterator(0, 0, 1, 1, ranges);
        List<String> seen = new ArrayList<>();
        int[] xyz = new int[3];
        while (it.hasNext()) {
            it.advance(xyz);
            seen.add(xyz[0] + "," + xyz[1] + "," + xyz[2]);
        }
        // the reference: z outermost, then y (ranges in order), then x fastest
        List<String> expected = new ArrayList<>();
        for (int z = 0; z <= 1; z++) for (int y : new int[] {1, 2, 5}) for (int x = 0; x <= 1; x++) expected.add(x + "," + y + "," + z);
        assertEquals(expected, seen);
        assertFalse(new BoxCellIterator(0, 0, 1, 1, new int[0]).hasNext());
    }

    @Test public void sparseBitsFindSetBitsAndAllocateOnlyUsedPages() {
        SparseBits bits = new SparseBits();
        assertFalse(bits.get(5));
        assertEquals(-1, bits.nextSetBit(0));
        bits.set(5);
        bits.set(70_000);
        bits.set(2_000_000_000);
        assertTrue(bits.get(5) && bits.get(70_000) && bits.get(2_000_000_000));
        assertFalse(bits.get(6));
        assertEquals(5, bits.nextSetBit(0));
        assertEquals(70_000, bits.nextSetBit(6));
        assertEquals(2_000_000_000, bits.nextSetBit(70_001));
        assertEquals(-1, bits.nextSetBit(2_000_000_001));
        assertEquals(3, bits.allocatedPages());
        bits.clear(5);
        assertFalse(bits.get(5));
        assertEquals(70_000, bits.nextSetBit(0));
    }

    private static long[] pack(int bits, long volume, long[][] entries) {
        TightLongBackedIntArray tight = new TightLongBackedIntArray(bits, volume);
        for (long[] e : entries) tight.setAt(e[0], (int) e[1]);
        return tight.getBackingLongArray();
    }

    @Test public void sparseArrayReadsLikeThePackedArray() {
        int sx = 40, sy = 35, sz = 21, bits = 3;
        long volume = (long) sx * sy * sz;
        long[][] entries = {{0, 1}, {1, 5}, {63, 2}, {64, 7}, {20_000, 4}, {volume - 1, 6}, {12_345, 3}};
        long[] words = pack(bits, volume, entries);
        SparseIntArray sparse = SparseIntArray.fromPacked(bits, sx, sy, sz, words);
        assertNotNull(sparse);
        TightLongBackedIntArray dense = new TightLongBackedIntArray(bits, volume, words);
        for (long i = 0; i < volume; i++) {
            if (sparse.getAt(i) != dense.getAt(i)) fail("index " + i + ": sparse " + sparse.getAt(i) + " dense " + dense.getAt(i));
        }
        assertEquals(volume, sparse.size());
        assertTrue("only the sections with blocks exist", sparse.allocatedSections() < sparse.totalSections());
        long[] counts = sparse.getValueCounts();
        long[] expected = dense.getValueCounts();
        assertArrayEquals(expected, counts);
    }

    @Test public void sparseArrayAllocatesOnlyWhereBlocksAre() {
        int sx = 160, sy = 80, sz = 160;
        long[] words = new long[(int) (((long) sx * sy * sz * 2 + 63) / 64)];
        TightLongBackedIntArray packed = new TightLongBackedIntArray(2, (long) sx * sy * sz, words);
        // one block in section (0,0,0) and one in the last section
        packed.setAt(0, 1);
        packed.setAt((long) sx * sy * sz - 1, 2);
        SparseIntArray sparse = SparseIntArray.fromPacked(2, sx, sy, sz, words);
        assertNotNull(sparse);
        assertEquals(2, sparse.allocatedSections());
        assertEquals(10 * 5 * 10, sparse.totalSections());
        assertTrue(sparse.sliceHasBlocks(0));
        assertTrue(sparse.sliceHasBlocks(4));
        assertFalse(sparse.sliceHasBlocks(2));
        assertTrue(SparseIntArray.worthIt(words));
        SparseIntArray copy = sparse.copy();
        assertEquals(1, copy.getAt(0));
        copy.setAt(0, 0);
        assertEquals("the copy is separate", 1, sparse.getAt(0));
        sparse.setAt(5, 3);
        assertEquals(3, sparse.getAt(5));
        assertEquals("a block in an existing section allocates nothing new", 2, sparse.allocatedSections());
    }

    @Test public void tooWideOrTooShortArraysAreRefused() {
        assertNull(SparseIntArray.fromPacked(17, 4, 4, 4, new long[100]));
        assertNull(SparseIntArray.fromPacked(2, 100, 100, 100, new long[3]));
        assertNull(SparseIntArray.fromPacked(2, 4, 4, 4, null));
    }

    @Test public void budgetCheckAndGzipSize() throws Exception {
        MemoryBudget.setBudgetForTest(1000);
        try {
            assertTrue(MemoryBudget.fits(1000));
            assertFalse(MemoryBudget.fits(1001));
        } finally {
            MemoryBudget.setBudgetForTest(0);
        }
        MemoryBudget.setMaxMemoryForTest(1L << 40);
        try {
            assertEquals(1L << 39, MemoryBudget.readBudget());
        } finally {
            MemoryBudget.setMaxMemoryForTest(0);
        }
        File file = File.createTempFile("perf", ".gz");
        try {
            try (GZIPOutputStream out = new GZIPOutputStream(new FileOutputStream(file))) {
                out.write(new byte[123_456]);
            }
            assertEquals(123_456L, MemoryBudget.gzipUnpackedSize(file.toPath()));
        } finally {
            file.delete();
        }
    }
}
