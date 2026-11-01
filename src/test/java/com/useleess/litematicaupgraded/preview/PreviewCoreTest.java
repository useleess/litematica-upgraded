package com.useleess.litematicaupgraded.preview;

import static org.junit.Assert.*;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import org.junit.Test;

public class PreviewCoreTest {
    /** A box filled by a rule. */
    private static BlockSelection.Grid grid(int sx, int sy, int sz, java.util.function.Predicate<int[]> solid) {
        return new BlockSelection.Grid() {
            public int sizeX() { return sx; }
            public int sizeY() { return sy; }
            public int sizeZ() { return sz; }
            public boolean solid(int x, int y, int z) { return solid.test(new int[] {x, y, z}); }
        };
    }

    private static final java.util.function.BooleanSupplier NEVER = () -> false;

    @Test public void turnsOnceEveryTwelveSeconds() {
        assertEquals(0f, PreviewMath.angle(0), 0.001f);
        assertEquals(90f, PreviewMath.angle(3000), 0.001f);
        assertEquals(180f, PreviewMath.angle(6000), 0.001f);
        assertEquals(0f, PreviewMath.angle(12000), 0.001f);
        assertEquals(30f, PreviewMath.angle(12000 * 5 + 1000), 0.001f);
        assertTrue(PreviewMath.angle(-1) > 359f);
        assertNotEquals(PreviewMath.angle(1000), PreviewMath.angle(2000), 0.001f);
    }

    /** The size of the turning model's shadow on the screen at an angle: width, and height as seen from the pitch. */
    private static double[] extent(int sx, int sy, int sz, double scale, int degrees) {
        double a = Math.toRadians(degrees), pitch = Math.toRadians(PreviewMath.PITCH_DEGREES);
        double minW = 1e9, maxW = -1e9, minH = 1e9, maxH = -1e9;
        for (int cx = 0; cx <= 1; cx++) for (int cy = 0; cy <= 1; cy++) for (int cz = 0; cz <= 1; cz++) {
            double x = (cx - 0.5) * sx, y = (cy - 0.5) * sy, z = (cz - 0.5) * sz;
            double rx = x * Math.cos(a) + z * Math.sin(a), rz = -x * Math.sin(a) + z * Math.cos(a);
            double sh = y * Math.cos(pitch) + rz * Math.sin(pitch);
            minW = Math.min(minW, rx); maxW = Math.max(maxW, rx);
            minH = Math.min(minH, sh); maxH = Math.max(maxH, sh);
        }
        return new double[] {(maxW - minW) * scale, (maxH - minH) * scale};
    }

    @Test public void theWholeSchematicFitsTheRectangleAtEveryAngle() {
        int[][] sizes = {{20, 10, 30}, {4, 120, 4}, {200, 6, 160}, {1, 1, 1}};
        int[][] views = {{100, 100}, {160, 60}, {60, 160}};
        for (int[] s : sizes) for (int[] v : views) {
            float scale = PreviewMath.scaleToFit(s[0], s[1], s[2], v[0], v[1], PreviewMath.PITCH_DEGREES);
            for (int deg = 0; deg <= 360; deg += 15) {
                double[] e = extent(s[0], s[1], s[2], scale, deg);
                assertTrue(s[0] + "x" + s[1] + "x" + s[2] + " in " + v[0] + "x" + v[1] + " at " + deg + ": " + e[0] + " wide",
                        e[0] <= v[0] + 0.01);
                assertTrue(s[0] + "x" + s[1] + "x" + s[2] + " in " + v[0] + "x" + v[1] + " at " + deg + ": " + e[1] + " high",
                        e[1] <= v[1] + 0.01);
            }
        }
        assertEquals(1f, PreviewMath.scaleToFit(0, 0, 0, 100, 100, PreviewMath.PITCH_DEGREES), 0f);
    }

    @Test public void aFlatWideSchematicIsDrawnLargerThanInTheBoundingSphere() {
        double radius = 0.5 * Math.sqrt(200.0 * 200 + 6 * 6 + 160 * 160);
        float sphere = (float) (100 * 0.5 * 0.94 / radius);
        assertTrue(PreviewMath.scaleToFit(200, 6, 160, 100, 100, PreviewMath.PITCH_DEGREES) > sphere);
    }

    @Test public void longestSideDecidesHowManyBlocksShareACell() {
        assertEquals(1, PreviewMath.stepFor(1));
        assertEquals(1, PreviewMath.stepFor(256));
        assertEquals(2, PreviewMath.stepFor(257));
        assertEquals(2, PreviewMath.stepFor(288));
        assertEquals(3, PreviewMath.stepFor(600));
        assertEquals(4, PreviewMath.stepFor(1000));
        for (int side : new int[] {1, 100, 256, 257, 511, 512, 513, 5000, 100_000}) {
            int step = PreviewMath.stepFor(side);
            assertTrue(side + " -> " + step, (side + step - 1) / step <= PreviewMath.MAX_SIDE);
        }
    }

    @Test public void aCellShowsItsMostCommonBlockAndATieGoesToTheOpaqueOne() {
        DownsampledGrid<String> grid = new DownsampledGrid<>(8, 8, 8, 2, "planks"::equals);
        for (int i = 0; i < 8; i++) grid.offer(i % 2, (i / 2) % 2, i / 4, i < 5 ? "glass" : "planks");
        assertEquals("glass", grid.get(0, 0, 0));
        for (int i = 0; i < 8; i++) grid.offer(2 + i % 2, (i / 2) % 2, i / 4, i < 4 ? "glass" : "planks");
        assertEquals("planks", grid.get(1, 0, 0));
        grid.offer(4, 0, 0, "glass");
        assertEquals("glass", grid.get(2, 0, 0));
        assertNull(grid.get(0, 1, 0));
        assertEquals(3, grid.count());
        grid.offer(6, 0, 0, "glass");
        grid.offer(7, 0, 0, "torch");
        assertEquals("glass", grid.get(3, 0, 0));
    }

    @Test public void aShrunkWallKeepsItsWindowAndTheCoarserMergeMatchesShrinkingDirectly() {
        DownsampledGrid<String> half = new DownsampledGrid<>(32, 12, 1, 2, "planks"::equals);
        DownsampledGrid<String> quarter = new DownsampledGrid<>(32, 12, 1, 4, "planks"::equals);
        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 12; y++) {
                String block = x >= 12 && x < 16 && y >= 4 && y < 8 ? "glass" : "planks";
                half.offer(x, y, 0, block);
                quarter.offer(x, y, 0, block);
            }
        }
        for (int cx = 6; cx < 8; cx++) for (int cy = 2; cy < 4; cy++) assertEquals("glass", half.get(cx, cy, 0));
        assertEquals("planks", half.get(5, 2, 0));
        DownsampledGrid<String> merged = half.coarser(2);
        assertEquals(quarter.count(), merged.count());
        for (int cx = 0; cx < 8; cx++) for (int cy = 0; cy < 3; cy++) assertEquals(quarter.get(cx, cy, 0), merged.get(cx, cy, 0));
        assertEquals("glass", merged.get(3, 1, 0));
    }

    @Test public void theShrunkSchematicHasAtMost256CellsPerSideAndShrinksFurther() {
        int step = PreviewMath.stepFor(1000);
        DownsampledGrid<String> grid = new DownsampledGrid<>(1000, 300, 600, step, "stone"::equals);
        assertEquals(250, grid.sizeX);
        assertEquals(75, grid.sizeY);
        assertEquals(150, grid.sizeZ);
        assertTrue(Math.max(grid.sizeX, Math.max(grid.sizeY, grid.sizeZ)) <= PreviewMath.MAX_SIDE);
        for (int x = 0; x < 1000; x += 4) grid.offer(x, 0, 0, "glass");
        grid.offer(1, 2, 3, "stone");
        DownsampledGrid<String> coarser = grid.coarser(2);
        assertEquals(8, coarser.unit);
        assertEquals(125, coarser.sizeX);
        assertEquals("glass", coarser.get(0, 0, 0));
        assertEquals(125, coarser.count());
    }

    @Test public void draggingTurnsAndTiltsAndTheTiltStopsAtStraightUpAndDown() {
        assertEquals(45f + 15f, PreviewMath.dragYaw(45f, 10), 0.001f);
        assertEquals(45f - 15f, PreviewMath.dragYaw(45f, -10), 0.001f);
        assertEquals(30f + 15f, PreviewMath.dragPitch(30f, 10), 0.001f);
        assertEquals(90f, PreviewMath.dragPitch(30f, 1000), 0f);
        assertEquals(-90f, PreviewMath.dragPitch(30f, -1000), 0f);
    }

    @Test public void theWheelZoomsBetweenAQuarterAndFourTimes() {
        assertEquals(1.25f, PreviewMath.zoomed(1f, 1), 0.001f);
        assertEquals(0.8f, PreviewMath.zoomed(1f, -1), 0.001f);
        float zoom = 1f;
        for (int i = 0; i < 50; i++) zoom = PreviewMath.zoomed(zoom, 1);
        assertEquals(4f, zoom, 0f);
        for (int i = 0; i < 100; i++) zoom = PreviewMath.zoomed(zoom, -1);
        assertEquals(0.25f, zoom, 0f);
    }

    @Test public void everyBlockIsLitFullBright() {
        assertEquals(15728880, PreviewMath.FULL_BRIGHT);
    }

    @Test public void theViewStartsTurnedAndLookingDown() {
        assertEquals(45f, PreviewMath.YAW_DEGREES, 0f);
        assertEquals(30f, PreviewMath.PITCH_DEGREES, 0f);
    }

    @Test public void theWholeSchematicFitsTheRectangleAtAnyTilt() {
        for (float pitch : new float[] {-90f, -45f, 0f, 30f, 60f, 90f}) {
            float scale = PreviewMath.scaleToFit(40, 80, 30, 100, 100, pitch);
            double a = Math.toRadians(pitch);
            double diagonal = Math.sqrt(40.0 * 40 + 30 * 30);
            assertTrue("width at " + pitch, diagonal * scale <= 100.01);
            assertTrue("height at " + pitch, (80 * Math.abs(Math.cos(a)) + diagonal * Math.abs(Math.sin(a))) * scale <= 100.01);
        }
    }

    private static LongArrayList solids(BlockSelection.Grid g, java.util.function.BooleanSupplier cancelled) {
        LongArrayList picked = new LongArrayList();
        BlockSelection.forEachSolid(g, cancelled, (x, y, z) -> picked.add(BlockSelection.pack(x, y, z)));
        return picked;
    }

    @Test public void airIsNeverPicked() {
        BlockSelection.Grid g = grid(4, 4, 4, p -> p[0] == p[1]);
        LongArrayList picked = solids(g, NEVER);
        assertEquals(16, picked.size());
        for (int i = 0; i < picked.size(); i++) {
            long p = picked.getLong(i);
            assertEquals(BlockSelection.x(p), BlockSelection.y(p));
        }
        assertEquals(0, solids(grid(5, 5, 5, p -> false), NEVER).size());
    }

    @Test public void packedPositionsComeBack() {
        long p = BlockSelection.pack(1234, 255, 98765);
        assertEquals(1234, BlockSelection.x(p));
        assertEquals(255, BlockSelection.y(p));
        assertEquals(98765, BlockSelection.z(p));
    }

    @Test public void countsTheBlocksThatAreNotAir() {
        assertEquals(1000, solids(grid(10, 10, 10, p -> true), NEVER).size());
        assertEquals(500, solids(grid(10, 10, 10, p -> p[1] < 5), NEVER).size());
        assertEquals(27000, solids(grid(30, 30, 30, p -> true), NEVER).size());
    }

    @Test public void cancellingStopsTheScan() {
        assertEquals(0, solids(grid(50, 50, 50, p -> true), () -> true).size());
    }

    @Test public void viewFillsTheRoomBelowTheDetails() {
        int[] view = PreviewLayout.view(100, 20, 170, 290, 120);
        assertNotNull(view);
        assertArrayEquals(new int[] {104, 124, 162, 20 + 290 - 4 - 124}, view);
        assertTrue(view[1] >= 120);
    }

    @Test public void viewShrinksWithLessRoomAndVanishesWhenTooSmall() {
        int[] shrunk = PreviewLayout.view(0, 0, 170, 290, 200);
        assertNotNull(shrunk);
        assertEquals(290 - 4 - 204, shrunk[3]);
        assertTrue(shrunk[1] >= 200);
        assertNull(PreviewLayout.view(0, 0, 170, 290, 270));
        assertNull(PreviewLayout.view(0, 0, 30, 290, 10));
    }

    @Test public void scissorBoxFollowsTheGuiScale() {
        // orthographic GUI projection for a 400 x 300 GUI on an 800 x 600 screen (scale 2), identity model view
        float[] projection = new float[16];
        projection[0] = 2f / 400;
        projection[5] = -2f / 300;
        projection[10] = -2f / 2000;
        projection[12] = -1f;
        projection[13] = 1f;
        projection[15] = 1f;
        float[] identity = new float[16];
        identity[0] = identity[5] = identity[10] = identity[15] = 1f;
        int[] box = ScissorMath.toPixels(identity, projection, new int[] {0, 0, 800, 600}, 100, 50, 80, 60);
        // GUI y runs down, the pixel box from the bottom: 600 - 2 * (50 + 60)
        assertArrayEquals(new int[] {200, 380, 160, 120}, box);
    }

    @Test public void sectionsCoverEveryBlockExactlyOnce() {
        DownsampledGrid<String> grid = new DownsampledGrid<>(50, 40, 33, 1, "stone"::equals);
        int offered = 0;
        for (int x = 0; x < 50; x++) for (int y = 0; y < 40; y++) for (int z = 0; z < 33; z++) {
            if ((x * 7 + y * 3 + z) % 5 != 0) { grid.offer(x, y, z, "stone"); offered++; }
        }
        java.util.List<LongArrayList> sections = PreviewSections.split(grid);
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (LongArrayList section : sections) {
            assertFalse(section.isEmpty());
            int first = -1;
            for (long packed : section) {
                assertTrue("block listed twice", seen.add(packed));
                int id = (BlockSelection.y(packed) >> 4) * 10000 + (BlockSelection.x(packed) >> 4) * 100 + (BlockSelection.z(packed) >> 4);
                if (first < 0) first = id;
                assertEquals("a section mixes blocks from two 16 cell boxes", first, id);
            }
        }
        assertEquals(offered, seen.size());
        assertEquals(3 * 4 * 3, sections.size());
    }

    @Test public void lowestSectionsComeFirst() {
        DownsampledGrid<String> grid = new DownsampledGrid<>(40, 40, 40, 1, "stone"::equals);
        grid.offer(30, 35, 30, "stone");
        grid.offer(1, 1, 1, "stone");
        grid.offer(20, 17, 2, "stone");
        java.util.List<LongArrayList> sections = PreviewSections.split(grid);
        assertEquals(3, sections.size());
        assertEquals(1, BlockSelection.y(sections.get(0).getLong(0)));
        assertEquals(17, BlockSelection.y(sections.get(1).getLong(0)));
        assertEquals(35, BlockSelection.y(sections.get(2).getLong(0)));
    }

    @Test public void stoppingTheRunnerSkipsTheSectionsNotStartedYet() throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.List<Integer> items = new java.util.ArrayList<>();
            for (int i = 0; i < 200; i++) items.add(i);
            java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
            SectionRunner.runAll(pool, items, () -> done.get() >= 5, i -> {
                done.incrementAndGet();
                try { Thread.sleep(5); } catch (InterruptedException e) { throw new IllegalStateException(e); }
            });
            assertTrue("did " + done.get(), done.get() >= 5 && done.get() < 20);
            done.set(0);
            SectionRunner.runAll(pool, items, () -> false, i -> done.incrementAndGet());
            assertEquals(200, done.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test public void aFailingSectionStopsTheRestAndIsPassedOn() throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
            try {
                SectionRunner.runAll(pool, java.util.Arrays.asList(1, 2, 3, 4), () -> false, i -> {
                    done.incrementAndGet();
                    if (i == 2) throw new IllegalStateException("boom");
                });
                fail("the failure was swallowed");
            } catch (IllegalStateException e) {
                assertEquals("boom", e.getMessage());
            }
            assertEquals(2, done.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test public void theCacheDropsTheOldestAndFreesIt() {
        java.util.List<String> freed = new java.util.ArrayList<>();
        LruCache<String, String> cache = new LruCache<>(3, freed::add);
        cache.put("a", "A");
        cache.put("b", "B");
        cache.put("c", "C");
        assertEquals("A", cache.get("a"));
        cache.put("d", "D");
        assertEquals(java.util.Collections.singletonList("B"), freed);
        assertNull(cache.get("b"));
        assertEquals(3, cache.size());
        assertTrue(cache.containsValue("A"));
        cache.put("a", "A2");
        assertEquals(java.util.Arrays.asList("B", "A"), freed);
        cache.clear();
        assertEquals(0, cache.size());
        assertEquals(5, freed.size());
        assertTrue(freed.containsAll(java.util.Arrays.asList("A2", "C", "D")));
    }

    @Test public void theRoughCopyIsSmallerThanTheShownLimit() {
        assertTrue(PreviewMath.ROUGH_BLOCKS < PreviewMath.MAX_BLOCKS);
        assertTrue(PreviewMath.UPLOAD_BUDGET_MS <= 5);
        assertEquals(16, PreviewMath.SECTION);
    }

    @Test public void theFullModelIsDrawnWhenBuiltElseTheShrunkOne() {
        assertEquals("shrunk", PreviewMath.modelToDraw("shrunk", "full", false));
        assertEquals("full", PreviewMath.modelToDraw("shrunk", "full", true));
        assertEquals("over the cap there is no full model", "shrunk", PreviewMath.modelToDraw("shrunk", null, true));
    }

    @Test public void theFullModelIsOnlyBuiltUnderTheBlockAndVertexCaps() {
        assertTrue(PreviewMath.fullModelAllowed(1000, 5000, PreviewMath.FULL_MAX_VERTICES));
        assertTrue(PreviewMath.fullModelAllowed(PreviewMath.FULL_MAX_BLOCKS, PreviewMath.FULL_MAX_VERTICES, PreviewMath.FULL_MAX_VERTICES));
        assertFalse(PreviewMath.fullModelAllowed(PreviewMath.FULL_MAX_BLOCKS + 1, 10, PreviewMath.FULL_MAX_VERTICES));
        assertFalse(PreviewMath.fullModelAllowed(10, PreviewMath.FULL_MAX_VERTICES + 1, PreviewMath.FULL_MAX_VERTICES));
        assertFalse(PreviewMath.fullModelAllowed(10, 61, 60));
        assertTrue("the cap stays near 150 MB of vertex data (28 bytes a vertex)", PreviewMath.FULL_MAX_VERTICES * 28 <= 160_000_000L);
    }

    @Test public void plainPositionsSplitIntoSectionsLikeCells() {
        LongArrayList positions = new LongArrayList();
        for (int x = 0; x < 40; x++) positions.add(BlockSelection.pack(x, 0, 0));
        java.util.List<LongArrayList> sections = PreviewSections.split(positions);
        assertEquals(3, sections.size());
        assertEquals(40, sections.stream().mapToInt(LongArrayList::size).sum());
    }

    @Test public void withTheFullModelTheZoomGoesOnUntilABlockIsEightPixelsWide() {
        assertEquals(PreviewMath.ZOOM_MAX, PreviewMath.maxZoom(5f), 0.001f);
        assertEquals(40f, PreviewMath.maxZoom(0.2f), 0.001f);
        assertEquals(PreviewMath.ZOOM_MAX, PreviewMath.maxZoom(0f), 0.001f);
        assertEquals(40f, PreviewMath.zoomed(39f, 1, 40f), 0.001f);
        assertEquals(40f, PreviewMath.zoomed(40f, 1, 40f), 0.001f);
    }
}
