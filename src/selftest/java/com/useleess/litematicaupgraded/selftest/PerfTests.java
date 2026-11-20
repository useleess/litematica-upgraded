package com.useleess.litematicaupgraded.selftest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.render.LitematicaRenderer;
import com.useleess.litematicaupgraded.litematica.schematic.LoadedSchematic;
import com.useleess.litematicaupgraded.litematica.schematic.SchematicRegion;
import com.useleess.litematicaupgraded.litematica.schematic.container.ArrayBlockContainer;
import com.useleess.litematicaupgraded.litematica.schematic.container.BlockContainer;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;
import com.useleess.litematicaupgraded.litematica.render.schematic.CompiledChunkSchematic;
import com.useleess.litematicaupgraded.litematica.render.schematic.RenderChunkSchematicVbo;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacementManager;
import com.useleess.litematicaupgraded.litematica.world.SchematicWorldHandler;
import com.useleess.litematicaupgraded.malilib.util.position.BlockPos;
import com.useleess.litematicaupgraded.malilib.util.position.BlockRotation;
import com.useleess.litematicaupgraded.malilib.util.position.IntBoundingBox;
import com.useleess.litematicaupgraded.malilib.util.world.BlockState;
import com.useleess.litematicaupgraded.malilib.config.option.ConfigOption;
import com.useleess.litematicaupgraded.malilib.config.option.BooleanConfig;
import com.useleess.litematicaupgraded.perf.DrawStats;
import com.useleess.litematicaupgraded.perf.GhostMove;
import com.useleess.litematicaupgraded.perf.MemoryBudget;
import com.useleess.litematicaupgraded.perf.PerfConfigs;
import com.useleess.litematicaupgraded.perf.PerfLive;
import com.useleess.litematicaupgraded.perf.PerfPlacementManager;
import com.useleess.litematicaupgraded.perf.PerfStats;
import com.useleess.litematicaupgraded.perf.PerfWorldRenderer;
import com.useleess.litematicaupgraded.perf.SparseIntArray;
import com.useleess.litematicaupgraded.printer.PrinterInit;

/**
 * perf/: the speed-ups (threaded builds, skipped empty slices, fast copy, ghost move, sparse storage), checked with a real
 * big schematic (a 1.16 file the downgrader loads) and generated huge sparse files. Timing comparisons (off vs on) were dropped
 * 2026-10-29: they flipped with the shared test box's load; the user judges the feel in game. Numbers are still printed as PERF lines.
 */
final class PerfTests {
    private static final String BIG = "main_storage_v5.litematic";

    private static LoadedSchematic big;
    private static SchematicPlacement bigPlacement;
    private static Map<Long, int[]> workerSignatures;
    private static Map<Long, Integer> workerRealWorld;
    private static long solidKey;
    private static net.minecraft.util.math.BlockPos solidWorldPos;
    private static IBlockState solidState;
    private static long hashFast;
    private static long copyNanosOn, copyNanosOff;
    private static final int NEAR = 56;
    /** Where the player stood when the perf tests began, so the later tests find the world as before. */
    static double[] home;

    private PerfTests() {}

    // ------------------------------------------------------------------------------------------------ small tools

    /** Steps run in order, one per tick at most when they are not ready; each returns true when done. */
    static final class Seq {
        private final List<Runner.Poll> steps = new ArrayList<>();
        private int index;

        Seq add(Runner.Poll step) {
            steps.add(step);
            return this;
        }

        /** Runs a body once. */
        Seq once(Runner.Body body) {
            steps.add(c -> {
                body.run(c);
                return true;
            });
            return this;
        }

        Seq ticks(int n) {
            int[] left = {n};
            steps.add(c -> left[0]-- <= 0);
            return this;
        }

        /** Waits until the condition holds for this many ticks in a row, or fails after the limit. */
        Seq until(String what, int limit, int stable, Runner.Poll condition) {
            int[] counts = new int[2];
            steps.add(c -> {
                if (++counts[1] > limit) throw new AssertionError("timed out waiting for " + what + " (" + status() + ")");
                counts[0] = condition.ready(c) ? counts[0] + 1 : 0;
                return counts[0] >= stable;
            });
            return this;
        }

        boolean run(Runner.Context c) throws Exception {
            while (index < steps.size()) {
                if (!steps.get(index).ready(c)) return false;
                index++;
            }
            return true;
        }
    }

    static PerfWorldRenderer renderer() {
        return (PerfWorldRenderer) LitematicaRenderer.getInstance().getWorldRenderer();
    }

    static SchematicPlacementManager manager() {
        return DataManager.getSchematicPlacementManager();
    }

    private static BlockPos cornerPos;

    private static String cornerInfo() {
        if (cornerPos == null) return "-";
        net.minecraft.util.math.BlockPos v = cornerPos.toVanillaPos();
        return renderer().perf$describeChunk(v.getX(), v.getY(), v.getZ()) + " boxes="
                + manager().getTouchedBoxesInSubChunk(new com.useleess.litematicaupgraded.malilib.util.position.ChunkSectionPos(v.getX() >> 4, v.getY() >> 4, v.getZ() >> 4)).size();
    }

    static String status() {
        return "builds=" + PerfStats.builds() + " mainBuilds=" + PerfStats.mainBuilds() + " threads=" + renderer().perf$buildThreads()
                + " idle=" + renderer().perf$isIdle() + " copies=" + ((PerfPlacementManager) manager()).perf$hasCopyableChunks()
                + " ghost=" + GhostMove.isActive() + " corner=" + cornerInfo();
    }

    static Path copyOut(String name) throws IOException {
        Path dir = Paths.get("selftest-tmp").toAbsolutePath();
        Files.createDirectories(dir);
        Path file = dir.resolve(name);
        try (InputStream in = PerfTests.class.getResourceAsStream("/selftest/" + name)) {
            Files.copy(in, file, StandardCopyOption.REPLACE_EXISTING);
        }
        return file;
    }

    static LoadedSchematic loadBig() throws IOException {
        if (big == null) {
            big = LoadedSchematic.tryLoadSchematic(copyOut(BIG)).orElseThrow(() -> new IllegalStateException("cannot load " + BIG));
        }
        return big;
    }

    static void removeAllPlacements() {
        SchematicPlacementManager m = manager();
        for (SchematicPlacement p : new ArrayList<>(m.getAllSchematicPlacements())) m.removeSchematicPlacement(p, true);
        bigPlacement = null;
    }

    /** A placement of the big schematic whose box starts a little beside the player. */
    static SchematicPlacement placeBig(Minecraft mc, String name) throws IOException {
        BlockPos origin = new BlockPos((int) mc.player.posX - 6, (int) mc.player.posY - 2, (int) mc.player.posZ - 6);
        SchematicPlacement p = SchematicPlacement.create(loadBig(), origin, name, true);
        manager().addSchematicPlacement(p, false);
        bigPlacement = p;
        return p;
    }

    static void fly(Minecraft mc, double x, double y, double z) {
        mc.player.capabilities.isFlying = true;
        mc.player.setPosition(x, y, z);
        mc.player.motionX = 0;
        mc.player.motionY = 0;
        mc.player.motionZ = 0;
    }

    static void teleport(Minecraft mc, double x, double y, double z) {
        mc.player.sendChatMessage("/tp @s " + x + " " + y + " " + z);
    }

    /** Ready when no copy is waiting, nothing is queued or being built, and a build has happened since {@code buildsBefore}. */
    static Runner.Poll settled(long[] buildsBefore) {
        return c -> !((PerfPlacementManager) manager()).perf$hasCopyableChunks() && renderer().perf$isIdle()
                && PerfStats.builds() > buildsBefore[0];
    }

    /**
     * Makes sure the earlier test's big placement is there: when it is missing (that test failed and cleaned up) a fresh one is
     * placed and built here, so one failure never makes the next tests fail too.
     */
    static void ensureBigPlacement(Runner.Context c, Seq seq, String name) throws IOException {
        if (bigPlacement != null && manager().getAllSchematicPlacements().contains(bigPlacement)) return;
        removeAllPlacements();
        resetSettings();
        long[] builds = {PerfStats.builds()};
        placeBig(c.mc, name);
        seq.until("the big placement to be copied and built", 3000, 20, settled(builds));
    }

    /**
     * The recorded build results of the chunks listed for drawing now (built and not waiting for a rebuild). A chunk out of
     * view (behind the frame or hidden behind built chunks) is not rebuilt when marked, so its last result can be outdated.
     */
    static Map<Long, int[]> currentSignatures(boolean mainThread) {
        Map<Long, int[]> all = PerfStats.signatures(mainThread);
        Map<Long, int[]> current = new LinkedHashMap<>();
        for (com.useleess.litematicaupgraded.litematica.render.schematic.RenderChunkSchematicVbo p : renderer().perf$listedRenderers()) {
            if (p.needsUpdate() || ((com.useleess.litematicaupgraded.perf.PerfChunk) p).perf$isBuilding()) continue;
            long key = p.getPosition().toLong();
            int[] sig = all.get(key);
            if (sig != null) current.put(key, sig);
        }
        return current;
    }

    /**
     * A fingerprint of the real world's blocks in each chunk section: the overlay part of a build compares the schematic with
     * the real world, so two builds of a chunk can only be expected to match while its real blocks stayed the same.
     */
    static Map<Long, Integer> realWorldPrints(Iterable<Long> chunkKeys) {
        World world = Minecraft.getMinecraft().world;
        net.minecraft.util.math.BlockPos.MutableBlockPos pos = new net.minecraft.util.math.BlockPos.MutableBlockPos();
        Map<Long, Integer> prints = new LinkedHashMap<>();
        for (long key : chunkKeys) {
            net.minecraft.util.math.BlockPos corner = net.minecraft.util.math.BlockPos.fromLong(key);
            int hash = 1;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        hash = 31 * hash + Block.getStateId(world.getBlockState(pos.setPos(corner.getX() + x, corner.getY() + y, corner.getZ() + z)));
                    }
                }
            }
            prints.put(key, hash);
        }
        return prints;
    }

    /**
     * Fails when a chunk's quads differ between two sets of build results; returns how many chunks were in both. A chunk whose
     * real blocks changed in between (aWorld: the fingerprints taken with a) is left out and logged, its builds had other input.
     */
    private static int compareSignatures(Runner.Context c, Map<Long, int[]> a, Map<Long, Integer> aWorld, Map<Long, int[]> b, String nameA, String nameB) {
        int compared = 0;
        Map<Long, Integer> bWorld = realWorldPrints(b.keySet());
        for (Map.Entry<Long, int[]> e : b.entrySet()) {
            int[] first = a.get(e.getKey());
            if (first == null) continue;
            if (!bWorld.get(e.getKey()).equals(aWorld.get(e.getKey()))) {
                System.out.println("PERF compare skipped chunk " + net.minecraft.util.math.BlockPos.fromLong(e.getKey()) + ": its real blocks changed between the " + nameA + " and the " + nameB);
                continue;
            }
            compared++;
            c.check(java.util.Arrays.equals(first, e.getValue()), "chunk " + net.minecraft.util.math.BlockPos.fromLong(e.getKey()) + ": " + nameA + " "
                    + java.util.Arrays.toString(first) + " " + nameB + " " + java.util.Arrays.toString(e.getValue()));
        }
        return compared;
    }

    /** Back to the automatic build threads (a failed threads test must not leave the single-thread mode on for the later tests). */
    private static void autoThreads() {
        PerfConfigs.BUILD_THREADS.setIntegerValue(0);
        PerfLive.consumeReload();
        renderer().perf$resetDispatcher();
    }

    /** Runs a test's steps; when one fails, the build threads are put back first. */
    private static Runner.Poll restoringThreads(Seq seq) {
        return c -> {
            try {
                return seq.run(c);
            } catch (Throwable e) {
                autoThreads();
                throw e;
            }
        };
    }

    /** A hash of every block of the schematic world inside the box. */
    private static long hashBox(IntBoundingBox box) {
        World world = SchematicWorldHandler.getSchematicWorld();
        Minecraft mc = Minecraft.getMinecraft();
        long hash = 1125899906842597L;
        net.minecraft.util.math.BlockPos.MutableBlockPos pos = new net.minecraft.util.math.BlockPos.MutableBlockPos();
        // only the part around the player, which the game has copied (a big box would take seconds)
        int x0 = Math.max(box.getMinX(), (int) mc.player.posX - NEAR), x1 = Math.min(box.getMaxX(), (int) mc.player.posX + NEAR);
        int z0 = Math.max(box.getMinZ(), (int) mc.player.posZ - NEAR), z1 = Math.min(box.getMaxZ(), (int) mc.player.posZ + NEAR);
        for (int y = box.getMinY(); y <= box.getMaxY(); y++) {
            for (int z = z0; z <= z1; z++) {
                for (int x = x0; x <= x1; x++) {
                    int id = Block.getStateId(world.getBlockState(pos.setPos(x, y, z)));
                    if (id != 0) hash = hash * 31 + id * 7919L + x * 31L + y * 17L + z;
                }
            }
        }
        return hash;
    }

    /** The first solid block of the big schematic the loaded part of the schematic world holds, for checks after a move. */
    private static void findSolid(SchematicPlacement p) {
        World world = SchematicWorldHandler.getSchematicWorld();
        IntBoundingBox box = p.getEnclosingBox();
        Minecraft mc = Minecraft.getMinecraft();
        net.minecraft.util.math.BlockPos.MutableBlockPos pos = new net.minecraft.util.math.BlockPos.MutableBlockPos();
        solidWorldPos = null;
        int x0 = Math.max(box.getMinX(), (int) mc.player.posX - NEAR), x1 = Math.min(box.getMaxX(), (int) mc.player.posX + NEAR);
        int z0 = Math.max(box.getMinZ(), (int) mc.player.posZ - NEAR), z1 = Math.min(box.getMaxZ(), (int) mc.player.posZ + NEAR);
        for (int y = box.getMinY(); y <= box.getMaxY() && solidWorldPos == null; y++) {
            for (int z = z0; z <= z1 && solidWorldPos == null; z++) {
                for (int x = x0; x <= x1; x++) {
                    IBlockState state = world.getBlockState(pos.setPos(x, y, z));
                    if (state.getBlock() != Blocks.AIR) {
                        solidWorldPos = new net.minecraft.util.math.BlockPos(x, y, z);
                        solidState = state;
                        break;
                    }
                }
            }
        }
    }

    static void resetSettings() {
        for (ConfigOption<?> option : PerfConfigs.OPTIONS) {
            if (option instanceof BooleanConfig) ((BooleanConfig) option).setBooleanValue(true);
        }
        PerfConfigs.BUILD_THREADS.setIntegerValue(0);
        PerfConfigs.FULL_COPY_BUDGET_MB.setIntegerValue(0);
        PerfConfigs.SHOW_STATS.setBooleanValue(true);
        PerfLive.consumeReload();
    }

    // ------------------------------------------------------------------------------------------------ the tests

    static void register(Runner.Registry r) {
        registerConfig(r);
        registerThreads(r);
        registerSlicesAndCopy(r);
        registerGhost(r);
        registerGhostBeside(r);
        registerSparse(r);
        registerDense(r);
        PerfDrawTests.register(r);
    }

    private static void registerConfig(Runner.Registry r) {
        r.waitFor("perf/config/settings-in-tab-default-on-applied-live", 600, c -> {
            home = new double[] {c.mc.player.posX, c.mc.player.posY, c.mc.player.posZ};
            c.check(PrinterInit.TAB != null, "the Litematica Upgraded tab does not exist");
            for (ConfigOption<?> option : PerfConfigs.OPTIONS) {
                c.check(PrinterInit.TAB.getConfigs().contains(option), option.getName() + " is not in the Litematica Upgraded tab");
                String name = "litematica_upgraded.config.name." + option.getName().toLowerCase();
                c.check(I18n.hasKey(name) && I18n.hasKey(name.replace(".name.", ".comment.")), "no name or tooltip translation for " + option.getName());
                // on by default, but the two draw-side speed-ups that measured as not paying off (see PerfConfigs): off
                boolean offByDefault = option == PerfConfigs.BATCH_DRAW;
                if (option instanceof BooleanConfig) c.check(((BooleanConfig) option).getBooleanValue() != offByDefault, option.getName() + " is not " + (offByDefault ? "off" : "on") + " by default");
            }
            c.check(PerfConfigs.BUILD_THREADS.getIntegerValue() == 0, "the thread count is not automatic by default");
            PerfConfigs.BUILD_THREADS.setIntegerValue(99);
            c.check(PerfConfigs.BUILD_THREADS.getIntegerValue() == 16, "99 threads was not limited to 16");
            PerfConfigs.BUILD_THREADS.setIntegerValue(3);
            c.check(PerfLive.consumeReload(), "changing the thread count did not ask for new build threads");
            renderer().perf$resetDispatcher();
            c.check(renderer().perf$buildThreads() == 3, "3 threads were asked for, " + renderer().perf$buildThreads() + " run");
            PerfConfigs.BUILD_THREADS.setIntegerValue(1);
            PerfLive.consumeReload();
            renderer().perf$resetDispatcher();
            c.check(renderer().perf$buildThreads() == 0, "1 thread should mean builds on the render thread, " + renderer().perf$buildThreads() + " threads run");
            PerfConfigs.BUILD_THREADS.setIntegerValue(0);
            PerfConfigs.THREADED_BUILD.setBooleanValue(false);
            c.check(PerfLive.consumeReload(), "switching threaded builds off did not take effect live");
            PerfConfigs.THREADED_BUILD.setBooleanValue(true);
            PerfLive.consumeReload();
            renderer().perf$resetDispatcher();
        }, c -> true);
    }

    private static void registerThreads(Runner.Registry r) {
        Seq threads = new Seq();
        long[] before = new long[3];
        r.waitFor("perf/threads/builds-run-on-build-threads", 12000, c -> {
            Runner.dismissPopups(c.mc);
            removeAllPlacements();
            resetSettings();
            PerfConfigs.BUILD_THREADS.setIntegerValue(4);
            PerfLive.consumeReload();
            renderer().perf$resetDispatcher();
            c.check(renderer().perf$buildThreads() == 4, "4 build threads did not start (" + renderer().perf$buildThreads() + ")");
            PerfStats.clearSignatures();
            before[0] = PerfStats.builds();
            before[1] = PerfStats.mainBuilds();
            placeBig(c.mc, "selftest-perf-threads");
            threads.until("the big placement to be copied and built", 3000, 20, settled(before)).once(x -> {
                x.check(PerfStats.builds() - before[0] > 10, "only " + (PerfStats.builds() - before[0]) + " chunk builds happened");
                x.check(PerfStats.mainBuilds() == before[1], (PerfStats.mainBuilds() - before[1]) + " builds ran on the render thread");
                workerSignatures = currentSignatures(false);
                workerRealWorld = realWorldPrints(workerSignatures.keySet());
                int solid = 0;
                for (int[] sig : workerSignatures.values()) if (sig[0] + sig[1] + sig[2] + sig[3] > 0) solid++;
                x.check(solid >= 3, "only " + solid + " built chunks hold any model quads");
                // fast camera moves and placement edits while builds run must not crash or hang
                System.out.println("PERF threads builds=" + (PerfStats.builds() - before[0]) + " solidChunks=" + solid);
            });
            // stress: jump around and mark everything dirty for a while
            int[] k = {0};
            threads.until("the stress moves to finish", 3000, 1, x -> {
                World world = SchematicWorldHandler.getSchematicWorld();
                IntBoundingBox box = bigPlacement.getEnclosingBox();
                double sx = x.mc.player.posX, sz = x.mc.player.posZ;
                fly(x.mc, sx + (k[0] % 2 == 0 ? 2 : -2), x.mc.player.posY, sz + (k[0] % 3 == 0 ? 2 : -1));
                if (k[0]++ % 4 == 0) world.markBlockRangeForRenderUpdate(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMinX() + 40, box.getMaxY(), box.getMinZ() + 40);
                return k[0] > 60;
            });
            threads.until("the builds after the stress", 3000, 20, x -> !((PerfPlacementManager) manager()).perf$hasCopyableChunks() && renderer().perf$isIdle()).once(x -> {
                // the chunks built again by the threads during the stress made the same quads as their first threaded build
                int again = compareSignatures(x, workerSignatures, workerRealWorld, currentSignatures(false), "first threaded build", "threaded rebuild");
                System.out.println("PERF threads rebuilt_same=" + again);
            });
        }, restoringThreads(threads));

        Seq same = new Seq();
        r.waitFor("perf/threads/same-result-as-single-thread-build", 12000, c -> {
            c.check(workerSignatures != null && !workerSignatures.isEmpty(), "the threaded build left nothing to compare with");
            long[] mainBefore = {PerfStats.mainBuilds()};
            PerfStats.signatures(true).clear();
            PerfConfigs.BUILD_THREADS.setIntegerValue(1);
            PerfLive.consumeReload();
            renderer().perf$resetDispatcher();
            same.once(x -> x.check(renderer().perf$buildThreads() == 0, "the single-thread mode still has build threads")).until("the single-thread build", 3000, 30, x -> !((PerfPlacementManager) manager()).perf$hasCopyableChunks() && renderer().perf$isIdle()
                    && PerfStats.mainBuilds() > mainBefore[0]).once(x -> {
                int compared = compareSignatures(x, workerSignatures, workerRealWorld, currentSignatures(true), "threaded", "single");
                x.check(compared >= 5, "only " + compared + " chunks were built both ways");
                System.out.println("PERF threads compared=" + compared + " chunks, same quads");
                autoThreads();
            });
        }, restoringThreads(same));

        Seq still = new Seq();
        long[] stillBefore = new long[3];
        r.waitFor("perf/nostutter/no-render-thread-builds-inside-placement", 12000, c -> {
            ensureBigPlacement(c, still, "selftest-perf-nostutter");
            still.once(x -> {
                x.check(renderer().perf$buildThreads() >= 1 || Runtime.getRuntime().availableProcessors() < 2, "no build threads");
                IntBoundingBox box = bigPlacement.getEnclosingBox();
                fly(x.mc, (box.getMinX() + box.getMaxX()) / 2.0, box.getMinY() + 3, (box.getMinZ() + box.getMaxZ()) / 2.0);
            }).until("the view to be built", 3000, 20, x -> !((PerfPlacementManager) manager()).perf$hasCopyableChunks() && renderer().perf$isIdle()).once(x -> {
                stillBefore[0] = PerfStats.builds();
                stillBefore[1] = PerfStats.mainNanos();
                stillBefore[2] = PerfStats.mainBuilds();
                World world = SchematicWorldHandler.getSchematicWorld();
                // everything around the camera is edited: all those chunks rebuild while the player stands inside
                world.markBlockRangeForRenderUpdate((int) x.mc.player.posX - 40, 0, (int) x.mc.player.posZ - 40, (int) x.mc.player.posX + 40, 255, (int) x.mc.player.posZ + 40);
            }).until("the rebuilds around the camera", 3000, 10, x -> PerfStats.builds() > stillBefore[0] + 5 && renderer().perf$isIdle()).once(x -> {
                x.check(PerfStats.mainBuilds() == stillBefore[2], (PerfStats.mainBuilds() - stillBefore[2]) + " chunk builds ran on the render thread, " + (PerfStats.mainNanos() - stillBefore[1]) / 1_000_000 + " ms");
                System.out.println("PERF nostutter rebuilds=" + (PerfStats.builds() - stillBefore[0]) + " renderThreadBuildMs=" + (PerfStats.mainNanos() - stillBefore[1]) / 1_000_000);
            });
        }, c -> still.run(c));
    }

    private static void registerSlicesAndCopy(Runner.Registry r) {
        Seq slices = new Seq();
        long[] mark = new long[4];
        Map<Long, int[]> onSignatures = new LinkedHashMap<>();
        Map<Long, Integer> onRealWorld = new LinkedHashMap<>();
        r.waitFor("perf/slices/empty-slices-skipped-nothing-missing", 12000, c -> {
            ensureBigPlacement(c, slices, "selftest-perf-slices");
            World world = SchematicWorldHandler.getSchematicWorld();
            slices.until("the view to be built", 3000, 20, x -> !((PerfPlacementManager) manager()).perf$hasCopyableChunks() && renderer().perf$isIdle()).once(x -> {
                onSignatures.clear();
                onSignatures.putAll(PerfStats.signatures(false));
                onRealWorld.clear();
                onRealWorld.putAll(realWorldPrints(onSignatures.keySet()));
                x.check(!onSignatures.isEmpty(), "no build signatures to compare");
                IntBoundingBox box = bigPlacement.getEnclosingBox();
                mark[0] = PerfStats.slicesScanned();
                mark[1] = PerfStats.slicesSkipped();
                // 1: with the speed-up on, rebuild everything and see that empty slices are counted as skipped
                fly(x.mc, (box.getMinX() + box.getMaxX()) / 2.0, box.getMinY() + 3, (box.getMinZ() + box.getMaxZ()) / 2.0);
                world.markBlockRangeForRenderUpdate((int) x.mc.player.posX - 60, 0, (int) x.mc.player.posZ - 60, (int) x.mc.player.posX + 60, 255, (int) x.mc.player.posZ + 60);
            }).until("the rebuild with empty slices skipped", 3000, 15, x -> PerfStats.slicesScanned() > mark[0] + 20 && renderer().perf$isIdle()).once(x -> {
                long scanned = PerfStats.slicesScanned() - mark[0], skipped = PerfStats.slicesSkipped() - mark[1];
                x.check(skipped > 0, "no empty slice was skipped out of " + scanned);
                System.out.println("PERF slices scanned=" + scanned + " skipped=" + skipped);
                mark[2] = PerfStats.slicesSkipped();
                mark[3] = PerfStats.slicesScanned();
                PerfConfigs.SKIP_EMPTY_SLICES.setBooleanValue(false);
                PerfStats.signatures(false).clear();
                world.markBlockRangeForRenderUpdate((int) x.mc.player.posX - 60, 0, (int) x.mc.player.posZ - 60, (int) x.mc.player.posX + 60, 255, (int) x.mc.player.posZ + 60);
            }).until("the rebuild with the speed-up off", 3000, 15, x -> PerfStats.slicesScanned() > mark[3] + 20 && renderer().perf$isIdle()).once(x -> {
                x.check(PerfStats.slicesSkipped() == mark[2], "slices were skipped while the setting was off");
                int compared = compareSignatures(x, onSignatures, onRealWorld, PerfStats.signatures(false), "build with empty slices skipped", "build with every slice scanned");
                x.check(compared >= 5, "only " + compared + " chunks to compare");
                PerfConfigs.SKIP_EMPTY_SLICES.setBooleanValue(true);
            });
        }, c -> slices.run(c));

        Seq copy = new Seq();
        long[] cm = new long[4];
        r.waitFor("perf/copy/fast-copy-same-blocks", 16000, c -> {
            removeAllPlacements();
            resetSettings();
            PerfConfigs.FAST_COPY.setBooleanValue(true);
            long[] builds = {PerfStats.builds()};
            cm[0] = PerfStats.copyFast();
            cm[1] = PerfStats.copyNanos();
            cm[2] = PerfStats.copyCalls();
            SchematicPlacement p;
            try {
                p = placeBig(c.mc, "selftest-perf-copy-on");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            copy.until("the copy with the fast path", 3000, 20, settled(builds)).once(x -> {
                x.check(PerfStats.copyFast() > cm[0], "no copy took the fast path");
                x.check(PerfStats.copyCellsSkipped() > 0, "no air cell was skipped");
                copyNanosOn = PerfStats.copyNanos() - cm[1];
                hashFast = hashBox(p.getEnclosingBox());
                cm[3] = PerfStats.copyFast();
                findSolid(p);
                x.check(solidWorldPos != null, "no block was copied into the schematic world");
                removeAllPlacements();
                PerfConfigs.FAST_COPY.setBooleanValue(false);
                cm[1] = PerfStats.copyNanos();
                builds[0] = PerfStats.builds();
                try {
                    placeBig(x.mc, "selftest-perf-copy-off");
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }).until("the copy without the fast path", 3000, 20, settled(builds)).once(x -> {
                x.check(PerfStats.copyFast() == cm[3], "the fast path ran while the setting was off");
                copyNanosOff = PerfStats.copyNanos() - cm[1];
                long hashSlow = hashBox(bigPlacement.getEnclosingBox());
                x.check(hashSlow == hashFast, "the schematic world differs between the fast and the plain copy");
                System.out.println(String.format("PERF copy_ms off=%.1f on=%.1f", copyNanosOff / 1.0e6, copyNanosOn / 1.0e6));
                                PerfConfigs.FAST_COPY.setBooleanValue(true);
            });
        }, c -> copy.run(c));
    }

    private static void registerGhost(Runner.Registry r) {
        Seq move = new Seq();
        long[] gm = new long[2];
        int[] ghostCount = new int[1];
        r.waitFor("perf/ghost/move-shifts-built-chunks-without-rebuild", 16000, c -> {
            removeAllPlacements();
            resetSettings();
            long[] builds = {PerfStats.builds()};
            SchematicPlacement p;
            try {
                p = placeBig(c.mc, "selftest-perf-ghost");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            move.until("the placement to be built", 3000, 30, settled(builds)).once(x -> {
                findSolid(p);
                x.check(solidWorldPos != null, "no block to follow");
                gm[0] = PerfStats.builds();
                gm[1] = PerfStats.ghostMoves();
                net.minecraft.util.math.BlockPos origin = p.getPosition().toVanillaPos();
                for (int i = 1; i <= 4; i++) {
                    manager().setOrigin(p, new BlockPos(origin.getX() + 3 * i, origin.getY(), origin.getZ() + 5 * i));
                }
                x.check(GhostMove.isActive(), "moving the placement did not start the ghosts");
                ghostCount[0] = GhostMove.renderers().size();
                x.check(ghostCount[0] > 0, "no built chunk became a ghost");
                x.check(PerfStats.ghostMoves() - gm[1] == 4, "4 moves were counted as " + (PerfStats.ghostMoves() - gm[1]));
                net.minecraft.util.math.BlockPos offset = GhostMove.currentOffset().toVanillaPos();
                x.check(offset.getX() == 12 && offset.getY() == 0 && offset.getZ() == 20, "the ghosts are shifted by " + offset + ", not (12, 0, 20)");
                for (net.minecraft.client.renderer.chunk.RenderChunk ghost : GhostMove.renderers()) {
                    net.minecraft.util.math.BlockPos g = ghost.getPosition();
                    x.check(((g.getX() - offset.getX()) & 15) == 0 && ((g.getY() - offset.getY()) & 15) == 0 && ((g.getZ() - offset.getZ()) & 15) == 0,
                            "a ghost at " + g + " is not a built chunk shifted by " + offset);
                }
            });
            // nothing may rebuild for as long as the move is held (400 ms after the last step: a busy PC may take more than a few
            // ticks for that, and the real rebuild then starts by design); judged on every tick of the held time, over 3 frames.
            // When the hold ended before that (a stall), the placement is moved again from a settled scene (out and back, the
            // same end position) and that hold is judged instead
            long[] heldFrom = new long[1];
            int[] retries = new int[1];
            move.once(x -> heldFrom[0] = DrawStats.frames()).until("3 frames of the held move", 6000, 1, x -> {
                if (GhostMove.isActive() && GhostMove.isHoldingWork()) {
                    x.check(PerfStats.builds() == gm[0], (PerfStats.builds() - gm[0]) + " chunks were rebuilt while the placement was moving");
                    return DrawStats.frames() >= heldFrom[0] + 3;
                }
                if (GhostMove.isActive() || !renderer().perf$isIdle() || ((PerfPlacementManager) manager()).perf$hasCopyableChunks()) return false;
                x.check(++retries[0] <= 10, "the move was never held for 3 frames in 10 tries");
                gm[0] = PerfStats.builds();
                net.minecraft.util.math.BlockPos at = p.getPosition().toVanillaPos();
                manager().setOrigin(p, new BlockPos(at.getX() + 3, at.getY(), at.getZ() + 5));
                manager().setOrigin(p, new BlockPos(at.getX(), at.getY(), at.getZ()));
                x.check(GhostMove.isActive(), "moving the placement again did not start the ghosts");
                heldFrom[0] = DrawStats.frames();
                return false;
            }).once(x -> {
                try {
                    Runner.shot(x.mc, "perf-ghost-moving");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            });
            move.until("the ghosts to be replaced by the real rebuild", 3000, 1, x -> !GhostMove.isActive()).once(x -> {
                x.check(PerfStats.builds() > gm[0], "no real rebuild happened after the move");
                World world = SchematicWorldHandler.getSchematicWorld();
                net.minecraft.util.math.BlockPos now = solidWorldPos.add(12, 0, 20);
                x.check(world.getBlockState(now) == solidState, "the block that was at " + solidWorldPos + " is not at " + now + " after the move: " + world.getBlockState(now));
                System.out.println("PERF ghost chunks=" + ghostCount[0] + " rebuilds_during_move=0");
            }).until("the real chunks to be built", 3000, 20, x -> renderer().perf$isIdle() && !((PerfPlacementManager) manager()).perf$hasCopyableChunks());
        }, c -> move.run(c));

        Seq rotate = new Seq();
        r.waitFor("perf/ghost/rotate-and-mirror-take-the-normal-path", 16000, c -> {
            ensureBigPlacement(c, rotate, "selftest-perf-rotate");
            long[] builds = new long[1];
            SchematicPlacement[] placement = new SchematicPlacement[1];
            rotate.until("the earlier move to settle", 3000, 20, x -> !GhostMove.isActive() && renderer().perf$isIdle() && !((PerfPlacementManager) manager()).perf$hasCopyableChunks()).once(x -> {
                SchematicPlacement p = bigPlacement;
                placement[0] = p;
                net.minecraft.util.math.BlockPos origin = p.getPosition().toVanillaPos();
                long ghostMovesBefore = PerfStats.ghostMoves();
                builds[0] = PerfStats.builds();
                manager().setOrigin(p, new BlockPos(origin.getX() + 2, origin.getY(), origin.getZ()));
                x.check(GhostMove.isActive(), "the move did not start the ghosts");
                manager().rotateBy(p, BlockRotation.CW_90);
                x.check(!GhostMove.isActive(), "a rotation did not end the ghosts");
                manager().setOrigin(p, new BlockPos(origin.getX() + 4, origin.getY(), origin.getZ()));
                manager().setRotation(p, BlockRotation.NONE);
                x.check(!GhostMove.isActive(), "setting the rotation did not end the ghosts");
                x.check(PerfStats.ghostMoves() - ghostMovesBefore >= 1, "the moves were not counted");
            }).until("the rebuild after the rotation", 3000, 30, x -> renderer().perf$isIdle() && !((PerfPlacementManager) manager()).perf$hasCopyableChunks()
                    && PerfStats.builds() > builds[0]).once(x -> x.check(!GhostMove.isActive(), "ghosts are active after a rotation"));
        }, c -> rotate.run(c));
    }

    /** The placements named in the manager's section list: name, whether still listed, enabled, and how many sections. */
    private static String touchedPlacements() {
        Map<SchematicPlacement, Integer> count = new java.util.IdentityHashMap<>();
        for (com.useleess.litematicaupgraded.malilib.util.position.ChunkSectionPos s : manager().getAllTouchedSubChunks()) {
            for (SchematicPlacementManager.PlacementPart part : manager().getAllPlacementsTouchingSubChunk(s)) count.merge(part.getPlacement(), 1, Integer::sum);
        }
        StringBuilder out = new StringBuilder();
        for (Map.Entry<SchematicPlacement, Integer> e : count.entrySet()) {
            SchematicPlacement p = e.getKey();
            out.append(p.getName()).append(manager().getAllSchematicPlacements().contains(p) ? " listed" : " REMOVED")
                    .append(p.isEnabled() ? " enabled " : " off ").append(e.getValue()).append("; ");
        }
        return out.toString();
    }

    /** The corners of the 16-block sections holding blocks of this placement (keys as BlockPos.toLong). */
    private static java.util.Set<Long> sectionsOf(SchematicPlacement p) {
        java.util.Set<Long> out = new java.util.HashSet<>();
        for (com.useleess.litematicaupgraded.malilib.util.position.ChunkSectionPos s : manager().getAllTouchedSubChunks()) {
            for (SchematicPlacementManager.PlacementPart part : manager().getAllPlacementsTouchingSubChunk(s)) {
                if (part.getPlacement() == p) out.add(new net.minecraft.util.math.BlockPos(s.getX() << 4, s.getY() << 4, s.getZ() << 4).toLong());
            }
        }
        return out;
    }

    /**
     * A second placement of the big schematic on the player's side (towards -x): {@code sectionsAway} empty 16-block sections
     * between the two, so its nearest chunks are in view; 0 = overlapping the first one, one block along.
     */
    private static SchematicPlacement placeBeside(SchematicPlacement p, int sectionsAway, String name) throws IOException {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        for (long key : sectionsOf(p)) {
            int x = net.minecraft.util.math.BlockPos.fromLong(key).getX();
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
        }
        net.minecraft.util.math.BlockPos origin = p.getPosition().toVanillaPos();
        int dx = sectionsAway == 0 ? 1 : -(maxX - minX + 16 * (sectionsAway + 2));
        SchematicPlacement q = SchematicPlacement.create(loadBig(), new BlockPos(origin.getX() + dx, origin.getY(), origin.getZ()), name, true);
        manager().addSchematicPlacement(q, false);
        return q;
    }

    /**
     * #15: smooth moving works when other placements are shown, as long as none shares a 16-block section with the moved one
     * (a section's chunk holds the blocks of every placement in it); the others stay drawn while it moves. A placement that
     * shares sections takes the normal path.
     */
    private static void registerGhostBeside(Runner.Registry r) {
        Seq beside = new Seq();
        r.waitFor("perf/ghost-beside/move-beside-another-placement", 16000, c -> {
            removeAllPlacements();
            resetSettings();
            long[] builds = {PerfStats.builds()};
            SchematicPlacement p, q;
            try {
                p = placeBig(c.mc, "selftest-perf-beside-a");
                q = placeBeside(p, 2, "selftest-perf-beside-b");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            java.util.Set<Long> qSections = new java.util.HashSet<>();
            beside.until("both placements to be built", 3000, 30, settled(builds)).once(x -> {
                qSections.addAll(sectionsOf(q));
                java.util.Set<Long> pOld = sectionsOf(p), pSections = new java.util.HashSet<>(pOld);
                pSections.retainAll(qSections);
                x.check(qSections.isEmpty() == false && pSections.isEmpty(), "the two placements share sections: " + pSections.size());
                net.minecraft.util.math.BlockPos origin = p.getPosition().toVanillaPos();
                for (int i = 1; i <= 4; i++) manager().setOrigin(p, new BlockPos(origin.getX(), origin.getY(), origin.getZ() + 5 * i));
                int built = 0;
                for (RenderChunkSchematicVbo rc : renderer().perf$listedRenderers()) {
                    if (rc.getChunkRenderData() != CompiledChunkSchematic.EMPTY && pOld.contains(rc.getPosition().toLong())) built++;
                }
                x.check(GhostMove.isActive(), "moving a placement beside another one did not start the smooth move (its sections "
                        + pOld.size() + ", the other's " + qSections.size() + ", built chunks where it was " + built + ", refused for a shared section "
                        + PerfStats.ghostSharedSection() + " / nothing built " + PerfStats.ghostNothingBuilt() + " times so far; in the section list: " + touchedPlacements() + ")");
                for (net.minecraft.client.renderer.chunk.RenderChunk ghost : GhostMove.renderers()) {
                    net.minecraft.util.math.BlockPos g = ghost.getPosition().add(-GhostMove.currentOffset().getX(), -GhostMove.currentOffset().getY(), -GhostMove.currentOffset().getZ());
                    x.check(qSections.contains(g.toLong()) == false, "the other placement's chunk at " + g + " moves along as a ghost");
                }
            }).until("a frame drawn while moving", 600, 1, x -> {
                if (GhostMove.isActive() == false) return false;
                for (RenderChunkSchematicVbo drawn : renderer().perf$lastDrawList()) {
                    if (qSections.contains(drawn.getPosition().toLong()) && drawn.getChunkRenderData() != CompiledChunkSchematic.EMPTY) return true;
                }
                return false;
            }).once(x -> {
                try {
                    Runner.shot(x.mc, "perf-ghost-beside-moving");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).until("the move to settle", 3000, 20, x -> !GhostMove.isActive() && renderer().perf$isIdle()
                    && !((PerfPlacementManager) manager()).perf$hasCopyableChunks()).once(x -> {
                x.check(manager().getAllSchematicPlacements().contains(q), "the other placement is gone");
                x.check(sectionsOf(q).equals(qSections), "the other placement's sections changed");
                removeAllPlacements();
                // the big schematic reaches above the world's top: its sections up there must go with it too
                x.check(touchedPlacements().isEmpty(), "removed placements left sections behind: " + touchedPlacements());
            });
        }, c -> beside.run(c));

        Seq overlap = new Seq();
        r.waitFor("perf/ghost-beside/overlapping-placement-takes-the-normal-path", 16000, c -> {
            removeAllPlacements();
            resetSettings();
            long[] builds = {PerfStats.builds()};
            SchematicPlacement p;
            try {
                p = placeBig(c.mc, "selftest-perf-overlap-a");
                placeBeside(p, 0, "selftest-perf-overlap-b");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            long[] movesBefore = new long[1];
            overlap.until("both placements to be built", 3000, 30, settled(builds)).once(x -> {
                movesBefore[0] = PerfStats.ghostMoves();
                net.minecraft.util.math.BlockPos origin = p.getPosition().toVanillaPos();
                manager().setOrigin(p, new BlockPos(origin.getX(), origin.getY(), origin.getZ() + 5));
                x.check(GhostMove.isActive() == false, "a placement sharing sections with another one started the smooth move");
                x.check(PerfStats.ghostMoves() == movesBefore[0], "the move was counted as a smooth move");
            }).until("the normal rebuild", 3000, 20, x -> renderer().perf$isIdle() && !((PerfPlacementManager) manager()).perf$hasCopyableChunks())
                    .once(x -> removeAllPlacements());
        }, c -> overlap.run(c));
    }

    private static Path hugeFile;
    private static CompletableFuture<Optional<LoadedSchematic>> hugeLoad;

    /** Writes a box of air with a few blocks in it and loads it, on a background thread (it takes seconds and the game must keep answering the server). */
    private static CompletableFuture<Optional<LoadedSchematic>> generateAndLoad(String name, int sx, int sy, int sz) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Path dir = Paths.get("selftest-tmp").toAbsolutePath();
                Files.createDirectories(dir);
                Map<net.minecraft.util.math.BlockPos, IBlockState> blocks = new LinkedHashMap<>();
                blocks.put(new net.minecraft.util.math.BlockPos(0, 0, 0), Blocks.STONE.getDefaultState());
                blocks.put(new net.minecraft.util.math.BlockPos(sx / 2, sy / 2, sz / 2), Blocks.GOLD_BLOCK.getDefaultState());
                blocks.put(new net.minecraft.util.math.BlockPos(sx / 2 + 1, sy / 2, sz / 2), Blocks.IRON_BLOCK.getDefaultState());
                blocks.put(new net.minecraft.util.math.BlockPos(sx - 1, sy - 1, sz - 1), Blocks.GOLD_BLOCK.getDefaultState());
                blocks.put(new net.minecraft.util.math.BlockPos(5, 3, 7), Blocks.STONE.getDefaultState());
                hugeFile = dir.resolve(name);
                LitematicWriter.writeSparse(hugeFile, sx, sy, sz, blocks);
                return LoadedSchematic.tryLoadSchematic(hugeFile);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static void checkSparseLoad(Runner.Context c, Optional<LoadedSchematic> loaded, long volume, int blocks) {
        c.check(loaded.isPresent(), "the " + volume + " cell file did not load");
        long cells = 0;
        for (SchematicRegion region : loaded.get().schematic.getRegions().values()) {
            BlockContainer container = region.getBlockContainer();
            c.check(container instanceof ArrayBlockContainer && ((ArrayBlockContainer) container).getIntStorage() instanceof SparseIntArray,
                    "the container is not sparse: " + container.getClass().getSimpleName());
            SparseIntArray storage = (SparseIntArray) ((ArrayBlockContainer) container).getIntStorage();
            cells += container.getTotalVolume();
            c.check(storage.allocatedSections() >= 1 && storage.allocatedSections() <= blocks,
                    storage.allocatedSections() + " sections hold memory for " + blocks + " blocks");
            c.check(storage.allocatedSections() * 100L < storage.totalSections(), "more than 1% of the " + storage.totalSections() + " sections were allocated");
            c.check(container.getTotalBlockCount() == blocks, "the container counts " + container.getTotalBlockCount() + " blocks, not " + blocks);
            System.out.println("PERF sparse cells=" + container.getTotalVolume() + " sections=" + storage.allocatedSections() + "/" + storage.totalSections());
        }
        c.check(cells == volume, "the loaded volume is " + cells + ", not " + volume);
    }

    /** Built and idle, and the listed pieces stopped changing (the server sent every chunk in view). */
    static Runner.Poll viewComplete(long[] buildsBefore) {
        Runner.Poll built = settled(buildsBefore);
        int[] last = {-1};
        return c -> {
            int listed = renderer().perf$listedRenderers().size();
            boolean same = listed == last[0];
            last[0] = listed;
            return same && built.ready(c);
        };
    }

    /** Whether the nearest listed piece with see-through blocks or overlay quads can be re-sorted (holds its copy). */
    static boolean nearestHasSortCopy(Runner.Context c) {
        RenderChunkSchematicVbo nearest = null;
        double best = Double.MAX_VALUE;
        for (RenderChunkSchematicVbo piece : renderer().perf$listedRenderers()) {
            CompiledChunkSchematic data = piece.getChunkRenderData();
            if (data == CompiledChunkSchematic.EMPTY || (data.isLayerEmpty(net.minecraft.util.BlockRenderLayer.TRANSLUCENT)
                    && data.isOverlayTypeEmpty(RenderChunkSchematicVbo.OverlayRenderType.QUAD))) continue;
            double d = piece.getPosition().add(8, 8, 8).distanceSq(c.mc.player.posX, c.mc.player.posY, c.mc.player.posZ);
            if (d < best) {
                best = d;
                nearest = piece;
            }
        }
        if (nearest == null) return true;
        if (nearest.getChunkRenderData().lacksSortCopy()) {
            // a resort pass happens when the camera moves: nudge it
            c.mc.player.setPosition(c.mc.player.posX + 0.6, c.mc.player.posY, c.mc.player.posZ);
            return false;
        }
        return true;
    }

    /** How many client chunks within the render distance are loaded, and where the camera is and looks. */
    static String loadedAround() {
        Minecraft mc = Minecraft.getMinecraft();
        int cx = (int) Math.floor(mc.player.posX) >> 4, cz = (int) Math.floor(mc.player.posZ) >> 4, rd = mc.gameSettings.renderDistanceChunks, loaded = 0;
        for (int x = cx - rd; x <= cx + rd; x++) {
            for (int z = cz - rd; z <= cz + rd; z++) {
                if (!mc.world.getChunkProvider().provideChunk(x, z).isEmpty()) loaded++;
            }
        }
        return "clientChunks=" + loaded + "/" + (2 * rd + 1) * (2 * rd + 1) + " cam=" + (int) mc.player.posX + "," + (int) mc.player.posY + ","
                + (int) mc.player.posZ + " yaw=" + (int) mc.player.rotationYaw + " pitch=" + (int) mc.player.rotationPitch;
    }

    /** Bytes of vertex copies the view's chunk renderers keep for re-sorting: block layers, overlays, how many copies, renderers. */
    static long[] keptVertexBytes() {
        long[] out = new long[4];
        try {
            Object frustum = net.minecraftforge.fml.relauncher.ReflectionHelper.findField(
                    com.useleess.litematicaupgraded.litematica.render.schematic.RenderGlobalSchematic.class, "viewFrustum").get(renderer());
            if (frustum == null) return out;
            net.minecraft.client.renderer.chunk.RenderChunk[] chunks = (net.minecraft.client.renderer.chunk.RenderChunk[])
                    net.minecraftforge.fml.relauncher.ReflectionHelper.findField(net.minecraft.client.renderer.ViewFrustum.class, "renderChunks", "field_178164_f").get(frustum);
            for (net.minecraft.client.renderer.chunk.RenderChunk chunk : chunks) {
                out[3]++;
                CompiledChunkSchematic data = chunk == null ? null : ((RenderChunkSchematicVbo) chunk).getChunkRenderData();
                if (data == null) continue;
                for (net.minecraft.util.BlockRenderLayer layer : net.minecraft.util.BlockRenderLayer.values()) {
                    net.minecraft.client.renderer.BufferBuilder.State state = data.getBlockBufferState(layer);
                    if (state != null) { out[0] += 4L * state.getRawBuffer().length; out[2]++; }
                }
                for (RenderChunkSchematicVbo.OverlayRenderType type : RenderChunkSchematicVbo.OverlayRenderType.values()) {
                    net.minecraft.client.renderer.BufferBuilder.State state = data.getOverlayBufferState(type);
                    if (state != null) { out[1] += 4L * state.getRawBuffer().length; out[2]++; }
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            System.out.println("PERF dense kept unavailable: " + e);
        }
        return out;
    }

    /** Java memory in use after a full collection, in MB, and the classes holding the most of it (#19 diagnosis). */
    static long reportMemory(String where) {
        System.gc();
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) >> 20;
        long[] kept = keptVertexBytes();
        System.out.println("PERF dense at=" + where + " usedMb=" + usedMb + " maxMb=" + (rt.maxMemory() >> 20)
                + " pieces=" + renderer().perf$renderedWithData() + " keptBlockMb=" + (kept[0] >> 20) + " keptOverlayMb=" + (kept[1] >> 20)
                + " keptStates=" + kept[2] + " renderers=" + kept[3] + " listed=" + renderer().perf$listedRenderers().size() + " " + loadedAround());
        try {
            Object histogram = java.lang.management.ManagementFactory.getPlatformMBeanServer().invoke(
                    new javax.management.ObjectName("com.sun.management:type=DiagnosticCommand"), "gcClassHistogram",
                    new Object[] {new String[0]}, new String[] {String[].class.getName()});
            String[] lines = String.valueOf(histogram).split("\n");
            for (int i = 0; i < lines.length; i++) {
                if (i < 12 || lines[i].contains("litematicaupgraded") || lines[i].contains("BufferBuilder")) System.out.println("PERF dense histo " + lines[i]);
            }
        } catch (Exception e) {
            System.out.println("PERF dense histo unavailable: " + e);
        }
        return usedMb;
    }

    private static CompletableFuture<Optional<LoadedSchematic>> denseLoad;

    /** A schematic as big and packed as the #19 report's (528 x 256 x 528, a third filled with mixed blocks), flown across. */
    private static void registerDense(Runner.Registry r) {
        Seq flight = new Seq();
        boolean[] started = {false};
        r.waitFor("perf/dense/528-packed-schematic-keeps-only-near-resort-copies", 40000, c -> {
            removeAllPlacements();
            resetSettings();
            reportMemory("start");
            List<IBlockState> mix = new ArrayList<>();
            for (EnumFacing f : EnumFacing.HORIZONTALS) mix.add(Blocks.OAK_STAIRS.getDefaultState().withProperty(net.minecraft.block.BlockStairs.FACING, f));
            mix.add(Blocks.GLASS.getDefaultState());
            mix.add(Blocks.STAINED_GLASS.getDefaultState());
            mix.add(Blocks.STONE_SLAB.getDefaultState());
            mix.add(Blocks.OAK_FENCE.getDefaultState());
            mix.add(Blocks.COBBLESTONE_WALL.getDefaultState());
            mix.add(Blocks.IRON_BARS.getDefaultState());
            mix.add(Blocks.GLASS_PANE.getDefaultState());
            mix.add(Blocks.LEAVES.getDefaultState());
            mix.add(Blocks.CARPET.getDefaultState());
            mix.add(Blocks.STONE.getDefaultState());
            mix.add(Blocks.PLANKS.getDefaultState());
            mix.add(Blocks.TORCH.getDefaultState());
            denseLoad = CompletableFuture.supplyAsync(() -> {
                try {
                    Path dir = Paths.get("selftest-tmp").toAbsolutePath();
                    Files.createDirectories(dir);
                    Path file = dir.resolve("perf-dense-528.litematic");
                    LitematicWriter.writeDense(file, 528, 256, 528, mix, 35);
                    return LoadedSchematic.tryLoadSchematic(file);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }, c -> {
            if (!started[0]) {
                if (!denseLoad.isDone()) return false;
                started[0] = true;
                Optional<LoadedSchematic> loaded = denseLoad.get();
                c.check(loaded.isPresent(), "the packed schematic did not load");
                reportMemory("loaded");
                c.mc.player.sendChatMessage("/gamemode 1");
                BlockPos origin = new BlockPos((int) c.mc.player.posX + 6, 0, (int) c.mc.player.posZ + 6);
                SchematicPlacement p = SchematicPlacement.create(loaded.get(), origin, "selftest-perf-dense", true);
                manager().addSchematicPlacement(p, false);
                int[][] stops = {{20, 20}, {140, 20}, {264, 20}, {400, 20}, {508, 20}, {508, 200}, {400, 264}, {264, 264}, {140, 400}, {20, 508}, {264, 508}, {508, 508}};
                for (int[] stop : stops) {
                    String where = stop[0] + "," + stop[1];
                    long[] builds = new long[1];
                    Runner.Poll complete = viewComplete(builds);
                    flight.once(x -> {
                        builds[0] = PerfStats.builds();
                        x.mc.player.sendChatMessage("/tp @s " + (origin.getX() + stop[0]) + " 140 " + (origin.getZ() + stop[1]) + " -45 20");
                    }).until("the camera to hover at " + where + ", height 140", 400, 10, x -> {
                        x.mc.player.capabilities.isFlying = true;
                        return Math.abs(x.mc.player.posY - 140) < 1 && Math.abs(x.mc.player.posX - (origin.getX() + stop[0] + 0.5)) < 1;
                    }).until("the view at " + where + " to be complete and built", 6000, 60, c2 -> {
                        c2.mc.player.capabilities.isFlying = true;
                        return complete.ready(c2);
                    }).once(x -> {
                        reportMemory(where);
                        if (where.equals("264,264")) {
                            try { Runner.shot(x.mc, "perf-dense-264"); } catch (Exception e) { throw new IllegalStateException(e); }
                        }
                        long[] kept = keptVertexBytes();
                        x.check(kept[0] + kept[1] < 160L << 20, "the pieces keep " + ((kept[0] + kept[1]) >> 20)
                                + " MB of re-sort copies at " + where + ", only the near ones should");
                    }).until("the nearest piece at " + where + " to hold its re-sort copy", 600, 5, x -> nearestHasSortCopy(x));
                }
                flight.once(x -> {
                    removeAllPlacements();
                    denseLoad = null;
                }).ticks(100).once(x -> reportMemory("removed"));
            }
            return flight.run(c);
        });
    }

    private static void registerSparse(Runner.Registry r) {
        Runner.Poll waitForHuge = c -> hugeLoad.isDone();
        r.waitFor("perf/sparse/24m-cell-box-memory-only-for-blocks", 6000, c -> {
            removeAllPlacements();
            resetSettings();
            hugeLoad = generateAndLoad("perf-24m.litematic", 480, 200, 250);
        }, c -> {
            if (!waitForHuge.ready(c)) return false;
            Optional<LoadedSchematic> loaded = hugeLoad.get();
            checkSparseLoad(c, loaded, 480L * 200 * 250, 5);
            return true;
        });

        Seq place = new Seq();
        long[] pm = new long[2];
        r.waitFor("perf/sparse/640m-cell-box-loads-places-renders", 18000, c -> {
            hugeLoad = generateAndLoad("perf-640m.litematic", 2500, 256, 1000);
        }, c -> {
            if (!waitForHuge.ready(c)) return false;
            if (pm[0] == 0) {
                pm[0] = 1;
                Optional<LoadedSchematic> loaded = hugeLoad.get();
                checkSparseLoad(c, loaded, 2500L * 256 * 1000, 5);
                // the corner block (0, 0, 0) lands a little ahead of the player, inside the loaded chunks, at the player's feet:
                // lower down the flat world's ground differs from it, so it would only get the wrong-block overlay, no model
                BlockPos origin = new BlockPos((int) c.mc.player.posX + 6, (int) Math.floor(c.mc.player.posY), (int) c.mc.player.posZ + 6);
                cornerPos = origin;
                // look at the corner (it is 6 blocks east and south of the player, a few blocks lower): only chunks in view are built
                c.mc.player.rotationYaw = -45;
                c.mc.player.rotationPitch = 30;
                SchematicPlacement p = SchematicPlacement.create(loaded.get(), origin, "selftest-perf-640m", true);
                long[] builds = {PerfStats.builds()};
                manager().addSchematicPlacement(p, false);
                bigPlacement = p;
                place.until("the huge placement's corner block", 3000, 5, x -> {
                    World world = SchematicWorldHandler.getSchematicWorld();
                    return world != null && world.getBlockState(origin.toVanillaPos()).getBlock() == Blocks.STONE;
                }).until("the huge placement to be built", 3000, 20, settled(builds)).until("the corner chunk to hold block models ("
                        + "touched=" + manager().getAllTouchedSubChunks().size() + ")", 3000, 5,
                        x -> renderer().perf$chunkHasModels(origin.toVanillaPos().getX(), origin.toVanillaPos().getY(), origin.toVanillaPos().getZ())).once(x -> {
                    int pieces = renderer().perf$renderedWithData();
                    x.check(pieces >= 1 && pieces <= 30, pieces + " render pieces hold built data, a handful was expected");
                    System.out.println("PERF sparse renderPieces=" + pieces + " usedMemoryMb=" + (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024));
                    try {
                        Runner.shot(x.mc, "perf-640m-placement");
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    removeAllPlacements();
                    hugeLoad = null;
                });
            }
            return place.run(c);
        });

        r.add("perf/sparse/out-of-memory-gives-a-message-not-a-crash", c -> {
            c.check(hugeFile != null && Files.exists(hugeFile), "no file to refuse");
            c.check(I18n.hasKey(MemoryBudget.OUT_OF_MEMORY_KEY), "the out-of-memory message has no translation");
            int before = MemoryBudget.refusals();
            MemoryBudget.setBudgetForTest(1024 * 1024);
            try {
                Optional<LoadedSchematic> refused = LoadedSchematic.tryLoadSchematic(hugeFile);
                c.check(!refused.isPresent(), "a file over the read budget was loaded");
                c.check(MemoryBudget.refusals() == before + 1, "the refusal was not counted / no message was shown");
            } finally {
                MemoryBudget.setBudgetForTest(0);
            }
        });
    }
}
