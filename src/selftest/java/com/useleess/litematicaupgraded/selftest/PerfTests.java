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
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacementManager;
import com.useleess.litematicaupgraded.litematica.world.SchematicWorldHandler;
import com.useleess.litematicaupgraded.malilib.util.position.BlockPos;
import com.useleess.litematicaupgraded.malilib.util.position.BlockRotation;
import com.useleess.litematicaupgraded.malilib.util.position.IntBoundingBox;
import com.useleess.litematicaupgraded.malilib.util.world.BlockState;
import com.useleess.litematicaupgraded.malilib.config.option.ConfigOption;
import com.useleess.litematicaupgraded.malilib.config.option.BooleanConfig;
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
        registerSparse(r);
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
        r.waitFor("perf/threads/builds-run-on-build-threads", 6000, c -> {
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
                workerSignatures = new LinkedHashMap<>(PerfStats.signatures(false));
                int solid = 0;
                for (int[] sig : workerSignatures.values()) if (sig[0] + sig[1] + sig[2] + sig[3] > 0) solid++;
                x.check(solid >= 3, "only " + solid + " built chunks hold any model quads");
                // fast camera moves and placement edits while builds run must not crash or hang
                System.out.println("PERF threads builds=" + (PerfStats.builds() - before[0]) + " solidChunks=" + solid);
            });
            // stress: jump around and mark everything dirty for a while
            int[] k = {0};
            threads.until("the stress moves to finish", 400, 1, x -> {
                World world = SchematicWorldHandler.getSchematicWorld();
                IntBoundingBox box = bigPlacement.getEnclosingBox();
                double sx = x.mc.player.posX, sz = x.mc.player.posZ;
                fly(x.mc, sx + (k[0] % 2 == 0 ? 2 : -2), x.mc.player.posY, sz + (k[0] % 3 == 0 ? 2 : -1));
                if (k[0]++ % 4 == 0) world.markBlockRangeForRenderUpdate(box.getMinX(), box.getMinY(), box.getMinZ(), box.getMinX() + 40, box.getMaxY(), box.getMinZ() + 40);
                return k[0] > 60;
            });
            threads.until("the builds after the stress", 3000, 20, x -> !((PerfPlacementManager) manager()).perf$hasCopyableChunks() && renderer().perf$isIdle());
        }, c -> threads.run(c));

        Seq same = new Seq();
        r.waitFor("perf/threads/same-result-as-single-thread-build", 6000, c -> {
            c.check(workerSignatures != null && !workerSignatures.isEmpty(), "the threaded build left nothing to compare with");
            long[] mainBefore = {PerfStats.mainBuilds()};
            PerfStats.signatures(true).clear();
            PerfConfigs.BUILD_THREADS.setIntegerValue(1);
            PerfLive.consumeReload();
            renderer().perf$resetDispatcher();
            c.check(renderer().perf$buildThreads() == 0, "the single-thread mode still has build threads");
            same.until("the single-thread build", 3000, 30, x -> !((PerfPlacementManager) manager()).perf$hasCopyableChunks() && renderer().perf$isIdle()
                    && PerfStats.mainBuilds() > mainBefore[0]).once(x -> {
                Map<Long, int[]> single = PerfStats.signatures(true);
                int compared = 0;
                for (Map.Entry<Long, int[]> e : single.entrySet()) {
                    int[] threaded = workerSignatures.get(e.getKey());
                    if (threaded == null) continue;
                    compared++;
                    x.check(java.util.Arrays.equals(threaded, e.getValue()),
                            "chunk " + net.minecraft.util.math.BlockPos.fromLong(e.getKey()) + ": threaded " + java.util.Arrays.toString(threaded)
                                    + " single " + java.util.Arrays.toString(e.getValue()));
                }
                x.check(compared >= 5, "only " + compared + " chunks were built both ways");
                System.out.println("PERF threads compared=" + compared + " chunks, same quads");
                PerfConfigs.BUILD_THREADS.setIntegerValue(0);
                PerfLive.consumeReload();
                renderer().perf$resetDispatcher();
            });
        }, c -> same.run(c));

        Seq still = new Seq();
        long[] stillBefore = new long[3];
        r.waitFor("perf/nostutter/no-render-thread-builds-inside-placement", 6000, c -> {
            c.check(bigPlacement != null, "the big placement of the earlier test is missing");
            c.check(renderer().perf$buildThreads() >= 1 || Runtime.getRuntime().availableProcessors() < 2, "no build threads");
            IntBoundingBox box = bigPlacement.getEnclosingBox();
            fly(c.mc, (box.getMinX() + box.getMaxX()) / 2.0, box.getMinY() + 3, (box.getMinZ() + box.getMaxZ()) / 2.0);
            still.ticks(20).once(x -> {
                stillBefore[0] = PerfStats.builds();
                stillBefore[1] = PerfStats.mainNanos();
                stillBefore[2] = PerfStats.mainBuilds();
                World world = SchematicWorldHandler.getSchematicWorld();
                // everything around the camera is edited: all those chunks rebuild while the player stands inside
                world.markBlockRangeForRenderUpdate((int) x.mc.player.posX - 40, 0, (int) x.mc.player.posZ - 40, (int) x.mc.player.posX + 40, 255, (int) x.mc.player.posZ + 40);
            }).until("the rebuilds around the camera", 1500, 10, x -> PerfStats.builds() > stillBefore[0] + 5 && renderer().perf$isIdle()).once(x -> {
                x.check(PerfStats.mainBuilds() == stillBefore[2], (PerfStats.mainBuilds() - stillBefore[2]) + " chunk builds ran on the render thread, " + (PerfStats.mainNanos() - stillBefore[1]) / 1_000_000 + " ms");
                System.out.println("PERF nostutter rebuilds=" + (PerfStats.builds() - stillBefore[0]) + " renderThreadBuildMs=" + (PerfStats.mainNanos() - stillBefore[1]) / 1_000_000);
            });
        }, c -> still.run(c));
    }

    private static void registerSlicesAndCopy(Runner.Registry r) {
        Seq slices = new Seq();
        long[] mark = new long[4];
        Map<Long, int[]> onSignatures = new LinkedHashMap<>();
        r.waitFor("perf/slices/empty-slices-skipped-nothing-missing", 6000, c -> {
            c.check(bigPlacement != null, "the big placement of the earlier test is missing");
            onSignatures.clear();
            onSignatures.putAll(PerfStats.signatures(false));
            c.check(!onSignatures.isEmpty(), "no build signatures to compare");
            IntBoundingBox box = bigPlacement.getEnclosingBox();
            mark[0] = PerfStats.slicesScanned();
            mark[1] = PerfStats.slicesSkipped();
            // 1: with the speed-up on, rebuild everything and see that empty slices are counted as skipped
            World world = SchematicWorldHandler.getSchematicWorld();
            fly(c.mc, (box.getMinX() + box.getMaxX()) / 2.0, box.getMinY() + 3, (box.getMinZ() + box.getMaxZ()) / 2.0);
            world.markBlockRangeForRenderUpdate((int) c.mc.player.posX - 60, 0, (int) c.mc.player.posZ - 60, (int) c.mc.player.posX + 60, 255, (int) c.mc.player.posZ + 60);
            slices.until("the rebuild with empty slices skipped", 2000, 15, x -> PerfStats.slicesScanned() > mark[0] + 20 && renderer().perf$isIdle()).once(x -> {
                long scanned = PerfStats.slicesScanned() - mark[0], skipped = PerfStats.slicesSkipped() - mark[1];
                x.check(skipped > 0, "no empty slice was skipped out of " + scanned);
                System.out.println("PERF slices scanned=" + scanned + " skipped=" + skipped);
                mark[2] = PerfStats.slicesSkipped();
                mark[3] = PerfStats.slicesScanned();
                PerfConfigs.SKIP_EMPTY_SLICES.setBooleanValue(false);
                PerfStats.signatures(false).clear();
                world.markBlockRangeForRenderUpdate((int) x.mc.player.posX - 60, 0, (int) x.mc.player.posZ - 60, (int) x.mc.player.posX + 60, 255, (int) x.mc.player.posZ + 60);
            }).until("the rebuild with the speed-up off", 2000, 15, x -> PerfStats.slicesScanned() > mark[3] + 20 && renderer().perf$isIdle()).once(x -> {
                x.check(PerfStats.slicesSkipped() == mark[2], "slices were skipped while the setting was off");
                Map<Long, int[]> off = PerfStats.signatures(false);
                int compared = 0;
                for (Map.Entry<Long, int[]> e : off.entrySet()) {
                    int[] on = onSignatures.get(e.getKey());
                    if (on == null) continue;
                    compared++;
                    x.check(java.util.Arrays.equals(on, e.getValue()), "chunk " + net.minecraft.util.math.BlockPos.fromLong(e.getKey()) + " differs with slices skipped: "
                            + java.util.Arrays.toString(on) + " vs " + java.util.Arrays.toString(e.getValue()));
                }
                x.check(compared >= 5, "only " + compared + " chunks to compare");
                PerfConfigs.SKIP_EMPTY_SLICES.setBooleanValue(true);
            });
        }, c -> slices.run(c));

        Seq copy = new Seq();
        long[] cm = new long[4];
        r.waitFor("perf/copy/fast-copy-same-blocks", 8000, c -> {
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
        r.waitFor("perf/ghost/move-shifts-built-chunks-without-rebuild", 8000, c -> {
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
            }).ticks(5).once(x -> {
                x.check(GhostMove.isActive(), "the ghosts ended before the placement settled");
                x.check(PerfStats.builds() == gm[0], (PerfStats.builds() - gm[0]) + " chunks were rebuilt while the placement was moving");
                try {
                    Runner.shot(x.mc, "perf-ghost-moving");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).until("the ghosts to be replaced by the real rebuild", 3000, 1, x -> !GhostMove.isActive()).once(x -> {
                x.check(PerfStats.builds() > gm[0], "no real rebuild happened after the move");
                World world = SchematicWorldHandler.getSchematicWorld();
                net.minecraft.util.math.BlockPos now = solidWorldPos.add(12, 0, 20);
                x.check(world.getBlockState(now) == solidState, "the block that was at " + solidWorldPos + " is not at " + now + " after the move: " + world.getBlockState(now));
                System.out.println("PERF ghost chunks=" + ghostCount[0] + " rebuilds_during_move=0");
            }).until("the real chunks to be built", 3000, 20, x -> renderer().perf$isIdle() && !((PerfPlacementManager) manager()).perf$hasCopyableChunks());
        }, c -> move.run(c));

        Seq rotate = new Seq();
        r.waitFor("perf/ghost/rotate-and-mirror-take-the-normal-path", 8000, c -> {
            c.check(bigPlacement != null && !GhostMove.isActive(), "no settled placement to rotate");
            SchematicPlacement p = bigPlacement;
            net.minecraft.util.math.BlockPos origin = p.getPosition().toVanillaPos();
            long ghostMovesBefore = PerfStats.ghostMoves();
            long[] builds = {PerfStats.builds()};
            manager().setOrigin(p, new BlockPos(origin.getX() + 2, origin.getY(), origin.getZ()));
            c.check(GhostMove.isActive(), "the move did not start the ghosts");
            manager().rotateBy(p, BlockRotation.CW_90);
            c.check(!GhostMove.isActive(), "a rotation did not end the ghosts");
            manager().setOrigin(p, new BlockPos(origin.getX() + 4, origin.getY(), origin.getZ()));
            manager().setRotation(p, BlockRotation.NONE);
            c.check(!GhostMove.isActive(), "setting the rotation did not end the ghosts");
            c.check(PerfStats.ghostMoves() - ghostMovesBefore >= 1, "the moves were not counted");
            rotate.until("the rebuild after the rotation", 3000, 30, x -> renderer().perf$isIdle() && !((PerfPlacementManager) manager()).perf$hasCopyableChunks()
                    && PerfStats.builds() > builds[0]).once(x -> x.check(!GhostMove.isActive(), "ghosts are active after a rotation"));
        }, c -> rotate.run(c));
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

    private static void registerSparse(Runner.Registry r) {
        Runner.Poll waitForHuge = c -> hugeLoad.isDone();
        r.waitFor("perf/sparse/24m-cell-box-memory-only-for-blocks", 3000, c -> {
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
        r.waitFor("perf/sparse/640m-cell-box-loads-places-renders", 9000, c -> {
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
                        + "touched=" + manager().getAllTouchedSubChunks().size() + ")", 600, 5,
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
