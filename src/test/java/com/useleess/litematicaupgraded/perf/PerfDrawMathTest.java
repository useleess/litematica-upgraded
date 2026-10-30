package com.useleess.litematicaupgraded.perf;

import static org.junit.Assert.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.Test;

public class PerfDrawMathTest {
    @Test public void workersLeaveOneCoreForTheGame() {
        assertEquals(1, PerfMath.workers(1, 8));
        assertEquals(1, PerfMath.workers(2, 8));
        assertEquals(3, PerfMath.workers(4, 8));
        assertEquals(15, PerfMath.workers(16, 32));
        assertEquals(1, PerfMath.workers(0, 8));
        // the memory limit wins over the core count (19 workers on a 20-thread PC with 2 GB would starve the game)
        assertEquals(6, PerfMath.workers(20, 6));
        assertEquals(1, PerfMath.workers(8, 0));
        assertEquals(6, PerfMath.memoryThreadCap(2048L * 1024 * 1024));
        assertEquals(1, PerfMath.memoryThreadCap(256L * 1024 * 1024));
    }

    @Test public void copyBudgetFromSliderThenCardThenFixed() {
        long mb = 1024L * 1024;
        // slider wins
        assertEquals(300 * mb, PerfMath.copyBudgetBytes(300, 8_000_000, 4_000_000));
        assertEquals("slider", PerfMath.copyBudgetSource(300, 8_000_000, 4_000_000));
        // NVX: 4 GB free -> 1 GB
        assertEquals(1024 * mb, PerfMath.copyBudgetBytes(0, 4L * 1024 * 1024, -1));
        assertEquals("nvx", PerfMath.copyBudgetSource(0, 4L * 1024 * 1024, -1));
        // ATI only: 2 GB free -> 512 MB
        assertEquals(512 * mb, PerfMath.copyBudgetBytes(0, -1, 2L * 1024 * 1024));
        assertEquals("ati", PerfMath.copyBudgetSource(0, -1, 2L * 1024 * 1024));
        // nothing reported -> 128 MB
        assertEquals(128 * mb, PerfMath.copyBudgetBytes(0, -1, -1));
        assertEquals("fixed", PerfMath.copyBudgetSource(0, -1, -1));
        assertEquals(128 * mb, PerfMath.copyBudgetBytes(0, 0, 0));
    }

    @Test public void javaMemoryLowBelowAQuarterFree() {
        long max = 4000;
        assertFalse(PerfMath.javaMemoryLow(max, 2000));
        assertFalse(PerfMath.javaMemoryLow(max, 3000));
        assertTrue(PerfMath.javaMemoryLow(max, 3001));
        assertTrue(PerfMath.javaMemoryLow(max, 3900));
    }

    @Test public void budgetIsRespected() {
        assertTrue(PerfMath.fitsBudget(0, 100, 100));
        assertFalse(PerfMath.fitsBudget(1, 100, 100));
        assertTrue(PerfMath.fitsBudget(50, 0, 50));
    }

    @Test public void resortOnlyAfterARealDistanceAndNotEveryFrame() {
        long later = PerfMath.RESORT_MIN_NANOS;
        assertFalse(PerfMath.shouldResort(1.5 * 1.5, later));       // stock would re-sort (> 1 block), the lazy rule not yet
        assertTrue(PerfMath.stockResort(1.5 * 1.5));
        assertTrue(PerfMath.shouldResort(2.0 * 2.0, later));
        assertFalse(PerfMath.shouldResort(10 * 10, later - 1));      // far, but too soon after the last one
        assertFalse(PerfMath.shouldResort(Double.NaN, later));       // a paused sort stays paused
        // a camera moving half a block per frame: stock re-sorts every 3rd frame, the lazy rule at most every 4 blocks of 0.5
        int stock = 0, lazy = 0;
        double sinceStock = 0, sinceLazy = 0;
        long nanos = 0, lastLazy = -PerfMath.RESORT_MIN_NANOS;
        for (int frame = 0; frame < 60; frame++) {
            sinceStock += 0.5;
            sinceLazy += 0.5;
            nanos += 33_000_000L;
            if (PerfMath.stockResort(sinceStock * sinceStock)) { stock++; sinceStock = 0; }
            if (PerfMath.shouldResort(sinceLazy * sinceLazy, nanos - lastLazy)) { lazy++; sinceLazy = 0; lastLazy = nanos; }
        }
        assertTrue("stock " + stock + " lazy " + lazy, lazy * 3 <= stock * 2);
        assertTrue(lazy > 0);
    }

    @Test public void farPiecesFromHalfTheRenderDistance() {
        assertEquals(3, PerfMath.farPieces(2));
        assertEquals(3, PerfMath.farPieces(6));
        assertEquals(6, PerfMath.farPieces(12));
        assertFalse(PerfMath.isFar(2, 0, 0, 0, 6));
        assertTrue(PerfMath.isFar(3, 0, 0, 0, 6));
        assertTrue(PerfMath.isFar(-1, -5, 0, 0, 12) == false);
        assertTrue(PerfMath.isFar(0, -6, 0, 0, 12));
    }

    @Test public void batchRegionsGroupFourByFourByFourPieces() {
        // pieces 0..3 share a region, 4 starts the next; negative pieces go to their own regions
        assertEquals(PerfMath.regionKey(0, 0, 0), PerfMath.regionKey(3, 3, 3));
        assertNotEquals(PerfMath.regionKey(0, 0, 0), PerfMath.regionKey(4, 0, 0));
        assertNotEquals(PerfMath.regionKey(0, 0, 0), PerfMath.regionKey(-1, 0, 0));
        assertEquals(PerfMath.regionKey(-4, 1, -1), PerfMath.regionKey(-1, 2, -3));
        assertNotEquals(PerfMath.regionKey(0, 0, 0), PerfMath.regionKey(0, 4, 0));
        assertNotEquals(PerfMath.regionKey(0, 0, 0), PerfMath.regionKey(0, 0, 4));
        assertEquals(0, PerfMath.regionOrigin(3));
        assertEquals(64, PerfMath.regionOrigin(4));
        assertEquals(-64, PerfMath.regionOrigin(-1));
        assertEquals(-64, PerfMath.regionOrigin(-4));
        assertEquals(-128, PerfMath.regionOrigin(-5));
    }

    @Test public void shiftedVerticesKeepEverythingButThePosition() {
        ByteBuffer b = ByteBuffer.allocate(2 * PerfMath.BLOCK_VERTEX_BYTES + 8).order(ByteOrder.nativeOrder());
        int start = 8;
        for (int v = 0; v < 2; v++) {
            int at = start + v * PerfMath.BLOCK_VERTEX_BYTES;
            b.putFloat(at, 1 + v);
            b.putFloat(at + 4, 2);
            b.putFloat(at + 8, 3);
            b.putInt(at + 12, 0x11223344);
            b.putFloat(at + 16, 0.5f);
            b.putFloat(at + 20, 0.25f);
            b.putInt(at + 24, 0x00F000F0);
        }
        PerfMath.shiftPositions(b, start, 2, 16, 32, 48);
        for (int v = 0; v < 2; v++) {
            int at = start + v * PerfMath.BLOCK_VERTEX_BYTES;
            assertEquals(17 + v, b.getFloat(at), 0);
            assertEquals(34, b.getFloat(at + 4), 0);
            assertEquals(51, b.getFloat(at + 8), 0);
            assertEquals(0x11223344, b.getInt(at + 12));
            assertEquals(0.5f, b.getFloat(at + 16), 0);
            assertEquals(0.25f, b.getFloat(at + 20), 0);
            assertEquals(0x00F000F0, b.getInt(at + 24));
        }
        assertEquals(0, b.getLong(0));
    }
}
