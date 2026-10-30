package com.useleess.litematicaupgraded.selftest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.block.BlockStainedGlass;
import net.minecraftforge.common.MinecraftForge;

import com.useleess.litematicaupgraded.litematica.render.schematic.RenderChunkSchematicVbo;
import com.useleess.litematicaupgraded.litematica.schematic.LoadedSchematic;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;
import com.useleess.litematicaupgraded.malilib.config.option.BooleanConfig;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.RenderWrap;
import com.useleess.litematicaupgraded.malilib.util.position.BlockPos;
import com.useleess.litematicaupgraded.malilib.util.position.IntBoundingBox;
import com.useleess.litematicaupgraded.perf.BatchDraw;
import com.useleess.litematicaupgraded.perf.CompleteCopy;
import com.useleess.litematicaupgraded.perf.DrawStats;
import com.useleess.litematicaupgraded.perf.FarDetail;
import com.useleess.litematicaupgraded.perf.GhostMove;
import com.useleess.litematicaupgraded.perf.GpuMemory;
import com.useleess.litematicaupgraded.perf.MemoryBudget;
import com.useleess.litematicaupgraded.perf.PerfChunk;
import com.useleess.litematicaupgraded.perf.PerfConfigs;
import com.useleess.litematicaupgraded.perf.PerfGuard;
import com.useleess.litematicaupgraded.perf.PerfLive;
import com.useleess.litematicaupgraded.perf.PerfMath;
import com.useleess.litematicaupgraded.perf.PerfPlacementManager;
import com.useleess.litematicaupgraded.perf.PerfStats;

import static com.useleess.litematicaupgraded.selftest.PerfTests.fly;
import static com.useleess.litematicaupgraded.selftest.PerfTests.manager;
import static com.useleess.litematicaupgraded.selftest.PerfTests.placeBig;
import static com.useleess.litematicaupgraded.selftest.PerfTests.removeAllPlacements;
import static com.useleess.litematicaupgraded.selftest.PerfTests.renderer;
import static com.useleess.litematicaupgraded.selftest.PerfTests.resetSettings;

/**
 * perf/ step 5b: the draw-side speed-ups (batched drawing, lazy see-through sorting, far detail, worker
 * count), the complete copy shown while a placement moves and its memory limit, "Use VBOs" off, a second A/B bench for
 * the draw side, and the short OptiFine phase. Numbers are printed as {@code PERF ...} lines.
 */
final class PerfDrawTests {
    private static SchematicPlacement matched;
    private static BlockPos matchedOrigin;
    private static double bigCopyMb;

    private PerfDrawTests() {}

    static void register(Runner.Registry r) {
        registerBatch(r);
        registerTranslucent(r);
        registerWorkers(r);
        registerFar(r);
        registerMoveFull(r);
        registerBudget(r);
        registerVbo(r);
        registerOptifine(r);
    }

    // ------------------------------------------------------------------------------------------------ small tools

    /**
     * A failing test must not leave its world changes, placements or settings behind: the later groups (the printer's)
     * run in the same world, from where the player stood before the perf tests.
     */
    private static void cleanUp(Minecraft mc) {
        try {
            System.clearProperty("litematicaupgraded.perf.cores");
            MemoryBudget.setMaxMemoryForTest(0);
            resetSettings();
            PerfGuard.resetForTest();
            removeAllPlacements();
            if (mc.gameSettings.useVbo == false) {
                mc.gameSettings.useVbo = true;
                mc.renderGlobal.loadRenderers();
            }
            if (matchedOrigin != null) command(mc, String.format("/fill %d %d %d %d %d %d minecraft:air", matchedOrigin.getX(), matchedOrigin.getY(), matchedOrigin.getZ(),
                    matchedOrigin.getX() + 11, matchedOrigin.getY() + 2, matchedOrigin.getZ() + 11));
            mc.player.capabilities.isFlying = false;
            if (PerfTests.home != null) PerfTests.teleport(mc, PerfTests.home[0], PerfTests.home[1], PerfTests.home[2]);
        } catch (RuntimeException e) {
            System.out.println("PERF cleanup after a failed test failed: " + e);
        }
    }

    /** Runs a test's steps; when one fails, cleans up first. */
    private static Runner.Poll cleaned(PerfTests.Seq seq) {
        return c -> {
            try {
                return seq.run(c);
            } catch (Throwable e) {
                cleanUp(c.mc);
                throw e;
            }
        };
    }

    private static Runner.Poll idle() {
        return c -> !((PerfPlacementManager) manager()).perf$hasCopyableChunks() && renderer().perf$isIdle() && !GhostMove.isActive();
    }

    private static void shot(Minecraft mc, String name) {
        try {
            Runner.shot(mc, name);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static SchematicPlacement big(Minecraft mc, String name) {
        try {
            return placeBig(mc, name);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The draw-side speed-ups as a player gets them (their defaults) or all off; the build-side ones stay as they are. */
    private static void drawSpeedups(boolean on) {
        for (BooleanConfig option : new BooleanConfig[] {PerfConfigs.BATCH_DRAW, PerfConfigs.LAZY_TRANSLUCENT_SORT, PerfConfigs.FAR_DETAIL}) {
            option.setBooleanValue(on && option.getDefaultBooleanValue());
        }
    }

    /** A fresh settled big placement with the player standing in it, looking along it. */
    private static void bigSettled(Runner.Context c, PerfTests.Seq seq, String name) {
        long[] builds = new long[1];
        seq.once(x -> {
            Runner.dismissPopups(x.mc);
            removeAllPlacements();
            resetSettings();
            PerfGuard.resetForTest();
            if (x.mc.player.posY < 4) PerfTests.teleport(x.mc, x.mc.player.posX, 6, x.mc.player.posZ);
        }).ticks(5).once(x -> {
            builds[0] = PerfStats.builds();
            big(x.mc, name);
        }).until("the big placement to be copied and built", 3000, 30, x -> PerfTests.settled(builds).ready(x) && !GhostMove.isActive());
    }

    /** How many of these pieces have built solid block models. */
    private static int solidPieces(List<RenderChunkSchematicVbo> pieces) {
        int n = 0;
        for (RenderChunkSchematicVbo p : pieces) if (p.getChunkRenderData() != null && p.getChunkRenderData().isLayerEmpty(net.minecraft.util.BlockRenderLayer.SOLID) == false) n++;
        return n;
    }

    /** Over the next {@code ticks} ticks: [frames, draw calls, block draws, listed, far outlines skipped, outline draws]. */
    private static void measure(PerfTests.Seq seq, int ticks, long[] out) {
        long[] start = new long[6];
        seq.once(c -> {
            start[0] = DrawStats.frames();
            start[1] = DrawStats.drawCalls();
            start[2] = DrawStats.blockDraws();
            start[3] = DrawStats.listed();
            start[4] = DrawStats.farOutlinesSkipped();
            start[5] = DrawStats.outlineDraws();
        }).ticks(ticks).once(c -> {
            out[0] = DrawStats.frames() - start[0];
            out[1] = DrawStats.drawCalls() - start[1];
            out[2] = DrawStats.blockDraws() - start[2];
            out[3] = DrawStats.listed() - start[3];
            out[4] = DrawStats.farOutlinesSkipped() - start[4];
            out[5] = DrawStats.outlineDraws() - start[5];
            if (out[0] < 5) throw new AssertionError("only " + out[0] + " frames were drawn in " + ticks + " ticks");
        });
    }

    private static double perFrame(long[] m, int i) {
        return m[i] / (double) Math.max(1, m[0]);
    }

    private static Path writeFile(String name, Map<net.minecraft.util.math.BlockPos, IBlockState> blocks, int sx, int sy, int sz) throws IOException {
        Path dir = Paths.get("selftest-tmp").toAbsolutePath();
        Files.createDirectories(dir);
        Path file = dir.resolve(name);
        LitematicWriter.writeSparse(file, sx, sy, sz, blocks);
        return file;
    }

    private static SchematicPlacement placeFile(Path file, BlockPos origin, String name) {
        LoadedSchematic loaded = LoadedSchematic.tryLoadSchematic(file).orElseThrow(() -> new IllegalStateException("cannot load " + file));
        SchematicPlacement p = SchematicPlacement.create(loaded, origin, name, true);
        manager().addSchematicPlacement(p, false);
        return p;
    }

    private static void command(Minecraft mc, String cmd) {
        mc.player.sendChatMessage(cmd);
    }

    /** Moves the placement by +dx (alternating between two offsets) every tick, so the move never settles. */
    private static Runner.Poll keepMoving(SchematicPlacement p, BlockPos from, int a, int b, int[] tick) {
        return c -> {
            int dx = (tick[0]++ % 2 == 0) ? a : b;
            manager().setOrigin(p, new BlockPos(from.getX() + dx, from.getY(), from.getZ()));
            return true;
        };
    }

    // ------------------------------------------------------------------------------------------------ batch

    private static void registerBatch(Runner.Registry r) {
        PerfTests.Seq seq = new PerfTests.Seq();
        long[] off = new long[6], on = new long[6];
        long[][] vertices = new long[2][];
        r.waitFor("perf/batch/draw-calls-per-frame-drop-clearly", 9000, c -> {
            bigSettled(c, seq, "selftest-perf-batch");
            seq.once(x -> {
                PerfConfigs.BATCH_DRAW.setBooleanValue(false);
                // a server teleport turns the view for sure (a turn only on this side can be put back by the server)
                command(x.mc, "/tp @s ~ ~ ~ 45 20");
            }).until("the view along the placement", 200, 20, x -> Math.abs(x.mc.player.rotationYaw - 45) < 1 && Math.abs(x.mc.player.rotationPitch - 20) < 1);
            measure(seq, 40, off);
            seq.once(x -> vertices[0] = DrawStats.lastFrameLayerVertices());
            seq.once(x -> PerfConfigs.BATCH_DRAW.setBooleanValue(true)).ticks(120);
            measure(seq, 40, on);
            seq.once(x -> {
                vertices[1] = DrawStats.lastFrameLayerVertices();
                double offCalls = perFrame(off, 2), onCalls = perFrame(on, 2);
                System.out.println(String.format("PERF batch_block_draw_calls_per_frame off=%.1f on=%.1f regions=%d", offCalls, onCalls, BatchDraw.regionCount()));
                x.check(BatchDraw.supported(), "the graphics card has no OpenGL 1.5");
                x.check(offCalls >= 10, "too few pieces are drawn to compare (" + offCalls + " draw calls per frame)");
                x.check(BatchDraw.regionCount() > 0, "no batch region was made");
                x.check(onCalls * 2 <= offCalls, String.format("block draw calls did not drop clearly: off %.1f, on %.1f per frame", offCalls, onCalls));
            });
        }, cleaned(seq));

        r.add("perf/batch/same-picture-same-vertices", c -> {
            c.check(vertices[0] != null && vertices[1] != null, "the earlier batch test left nothing to compare");
            System.out.println("PERF batch_vertices off=" + Arrays.toString(vertices[0]) + " on=" + Arrays.toString(vertices[1]));
            c.check(vertices[0][0] > 0, "no solid vertices were drawn");
            for (int layer = 0; layer < 3; layer++) {
                c.check(vertices[0][layer] == vertices[1][layer], "layer " + layer + " drew " + vertices[0][layer] + " vertices one by one but "
                        + vertices[1][layer] + " batched (not the same picture)");
            }
            shot(c.mc, "perf-batch-on");
        });
    }

    // ------------------------------------------------------------------------------------------------ translucent

    private static void registerTranslucent(Runner.Registry r) {
        PerfTests.Seq seq = new PerfTests.Seq();
        long[] resorts = new long[2];
        double[] start = new double[3];
        r.waitFor("perf/translucent/resort-only-after-real-distance", 6000, c -> {
            Runner.dismissPopups(c.mc);
            removeAllPlacements();
            resetSettings();
            Map<net.minecraft.util.math.BlockPos, IBlockState> glass = new LinkedHashMap<>();
            IBlockState state = Blocks.STAINED_GLASS.getDefaultState().withProperty(BlockStainedGlass.COLOR, EnumDyeColor.LIGHT_BLUE);
            for (int x = 0; x < 40; x += 2) for (int z = 0; z < 40; z += 2) glass.put(new net.minecraft.util.math.BlockPos(x, 0, z), state);
            Path file = writeFile("perf-glass.litematic", glass, 40, 1, 40);
            start[0] = c.mc.player.posX;
            start[1] = Math.max(c.mc.player.posY, 8) + 4;
            start[2] = c.mc.player.posZ;
            long[] builds = {PerfStats.builds()};
            placeFile(file, new BlockPos((int) start[0] - 20, (int) start[1] - 4, (int) start[2] - 20), "selftest-perf-glass");
            seq.until("the glass placement", 3000, 20, PerfTests.settled(builds));
            for (int round = 0; round < 2; round++) {
                boolean lazy = round == 1;
                int[] t = {0};
                long[] before = new long[1];
                seq.once(x -> {
                    PerfConfigs.LAZY_TRANSLUCENT_SORT.setBooleanValue(lazy);
                    fly(x.mc, start[0] - 10, start[1], start[2]);
                    x.mc.player.rotationPitch = 60;
                }).ticks(20).once(x -> before[0] = DrawStats.resorts()).add(x -> {
                    // 0.4 blocks a tick, 50 ticks: 20 blocks
                    fly(x.mc, start[0] - 10 + 0.4 * t[0], start[1], start[2]);
                    return ++t[0] > 50;
                }).once(x -> resorts[lazy ? 1 : 0] = DrawStats.resorts() - before[0]);
            }
            seq.once(x -> {
                System.out.println("PERF translucent_resorts_over_20_blocks off=" + resorts[0] + " on=" + resorts[1]);
                x.check(resorts[0] >= 5, "the stock rule re-sorted only " + resorts[0] + " times over 20 blocks");
                x.check(resorts[1] >= 2, "the lazy rule never re-sorted over 20 blocks (" + resorts[1] + ")");
                x.check(resorts[1] < resorts[0], "the lazy rule re-sorted as often as the stock one (" + resorts[1] + " vs " + resorts[0] + ")");
                x.check(resorts[1] <= 20 / PerfMath.RESORT_DISTANCE + 1, resorts[1] + " re-sorts over 20 blocks is more than one per " + PerfMath.RESORT_DISTANCE + " blocks");
                resetSettings();
                removeAllPlacements();
            });
        }, cleaned(seq));
    }

    // ------------------------------------------------------------------------------------------------ workers

    private static int cores() {
        return Runtime.getRuntime().availableProcessors();
    }

    /** The automatic worker count of this PC (cores - 1, within Litematica's memory limit). */
    private static int autoWorkers() {
        return PerfMath.workers(cores(), PerfMath.memoryThreadCap(Runtime.getRuntime().maxMemory()));
    }

    private static void resetWorkers() {
        PerfLive.consumeReload();
        renderer().perf$resetDispatcher();
    }

    private static void registerWorkers(Runner.Registry r) {
        r.add("perf/workers/cores-minus-one-at-least-one", c -> {
            removeAllPlacements();
            resetSettings();
            try {
                resetWorkers();
                c.check(renderer().perf$buildThreads() == autoWorkers(), cores() + " cores gave " + renderer().perf$buildThreads() + " workers, not " + autoWorkers());
                System.setProperty("litematicaupgraded.perf.cores", "2");
                resetWorkers();
                c.check(renderer().perf$buildThreads() == 1, "2 cores gave " + renderer().perf$buildThreads() + " workers, not 1 (one core left for the game)");
                System.setProperty("litematicaupgraded.perf.cores", "1");
                resetWorkers();
                c.check(renderer().perf$buildThreads() == 1, "1 core gave " + renderer().perf$buildThreads() + " workers, not at least 1");
                System.clearProperty("litematicaupgraded.perf.cores");
                PerfConfigs.BUILD_THREADS.setIntegerValue(3);
                resetWorkers();
                c.check(renderer().perf$buildThreads() == 3, "the setting asked for 3 workers, " + renderer().perf$buildThreads() + " run");
                PerfConfigs.BUILD_THREADS.setIntegerValue(0);
                System.out.println("PERF workers cores=" + cores() + " memory_cap=" + PerfMath.memoryThreadCap(Runtime.getRuntime().maxMemory()) + " auto=" + autoWorkers());
            } finally {
                System.clearProperty("litematicaupgraded.perf.cores");
                PerfConfigs.BUILD_THREADS.setIntegerValue(0);
                resetWorkers();
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ far

    private static void registerFar(Runner.Registry r) {
        PerfTests.Seq seq = new PerfTests.Seq();
        long[] on = new long[6], off = new long[6];
        r.waitFor("perf/far/far-pieces-without-outline-lines", 9000, c -> {
            bigSettled(c, seq, "selftest-perf-far");
            seq.once(x -> {
                // stand at one corner of the placement so its far end is more than 3 pieces away
                IntBoundingBox box = manager().getAllSchematicPlacements().get(0).getEnclosingBox();
                fly(x.mc, box.getMinX() + 1, box.getMinY() + 8, box.getMinZ() + 1);
                x.mc.player.rotationYaw = -45;
                x.mc.player.rotationPitch = 25;
            }).ticks(40);
            measure(seq, 30, on);
            seq.once(x -> PerfConfigs.FAR_DETAIL.setBooleanValue(false)).ticks(5);
            measure(seq, 30, off);
            seq.once(x -> {
                PerfConfigs.FAR_DETAIL.setBooleanValue(true);
                System.out.println(String.format("PERF far_outline_draws_per_frame on=%.1f off=%.1f skipped_on=%.1f", perFrame(on, 5), perFrame(off, 5), perFrame(on, 4)));
                x.check(on[4] > 0, "no far piece was drawn without its outline lines");
                x.check(on[5] > 0, "near pieces lost their outline lines too");
                x.check(off[4] == 0, "outline lines were left out with the setting off");
                x.check(perFrame(off, 5) > perFrame(on, 5), "not fewer outline draws with less detail far away");
            });
        }, cleaned(seq));

        PerfTests.Seq low = new PerfTests.Seq();
        long[] lowMem = new long[6];
        r.waitFor("perf/far/off-by-itself-on-low-memory", 3000, c -> {
            Runtime rt = Runtime.getRuntime();
            long used = rt.totalMemory() - rt.freeMemory();
            MemoryBudget.setMaxMemoryForTest(used + used / 10);
            low.ticks(5);
            measure(low, 30, lowMem);
            low.once(x -> {
                MemoryBudget.setMaxMemoryForTest(0);
                x.check(lowMem[4] == 0, lowMem[4] + " outline lines were left out although Java memory was low");
                x.check(lowMem[5] > 0, "no outline was drawn at all");
                x.check(FarDetail.active(), "far detail did not come back once memory was fine");
                removeAllPlacements();
            });
        }, c -> {
            try {
                return low.run(c);
            } catch (Throwable e) {
                cleanUp(c.mc);
                throw e;
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ complete copy while moving

    private static int solidQuads(List<RenderChunkSchematicVbo> pieces) {
        long bytes = 0;
        for (RenderChunkSchematicVbo p : pieces) bytes += ((PerfChunk) p).perf$uploadedBytes();
        return (int) (bytes / PerfMath.BLOCK_VERTEX_BYTES / 4);
    }

    private static void registerMoveFull(Runner.Registry r) {
        PerfTests.Seq seq = new PerfTests.Seq();
        int[] tick = {0};
        r.waitFor("perf/movefull/blocks-matching-the-world-shown-while-moving", 9000, c -> {
            Runner.dismissPopups(c.mc);
            removeAllPlacements();
            resetSettings();
            PerfGuard.resetForTest();
            // a 12 x 3 x 12 stone schematic placed exactly where the world gets the same stone: every block matches
            Map<net.minecraft.util.math.BlockPos, IBlockState> stone = new LinkedHashMap<>();
            for (int x = 0; x < 12; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 12; z++) stone.put(new net.minecraft.util.math.BlockPos(x, y, z), Blocks.STONE.getDefaultState());
            Path file = writeFile("perf-matched.litematic", stone, 12, 3, 12);
            int ox = (int) Math.floor(c.mc.player.posX) + 4, oy = Math.max((int) Math.floor(c.mc.player.posY), 8) + 2, oz = (int) Math.floor(c.mc.player.posZ) + 4;
            matchedOrigin = new BlockPos(ox, oy, oz);
            command(c.mc, String.format("/fill %d %d %d %d %d %d minecraft:stone", ox, oy, oz, ox + 11, oy + 2, oz + 11));
            fly(c.mc, ox - 6, oy + 6, oz - 6);
            c.mc.player.rotationYaw = -45;
            c.mc.player.rotationPitch = 35;
            seq.until("the stone in the world", 600, 5, x -> x.mc.world.getBlockState(new net.minecraft.util.math.BlockPos(ox + 11, oy + 2, oz + 11)).getBlock() == Blocks.STONE);
            long[] builds = new long[1];
            seq.once(x -> {
                builds[0] = PerfStats.builds();
                matched = placeFile(file, matchedOrigin, "selftest-perf-matched");
                manager().setSelectedSchematicPlacement(matched);
            }).until("the matched placement to be built", 3000, 30, x -> PerfTests.settled(builds).ready(x));
            seq.once(x -> {
                x.check(manager().getSelectedSchematicPlacement() == matched, "the placement could not be selected");
                x.check(solidPieces(renderer().perf$lastDrawList()) == 0, solidPieces(renderer().perf$lastDrawList()) + " normal pieces already draw the matching stone blocks");
            });
            // move it (6 and 7 blocks east, back and forth every tick) until the complete copy is built
            seq.until("the complete copy while moving", 2000, 1, x -> {
                keepMoving(matched, matchedOrigin, 6, 7, tick).ready(x);
                return GhostMove.isActive() && CompleteCopy.isKeptFor(matched) && CompleteCopy.isBuilding() == false && CompleteCopy.keptCount() > 0;
            }).once(x -> {
                // the camera looks at the moved copy (beside the world's stone) for the screenshot; a server teleport, so the
                // server does not put the player back
                x.mc.player.capabilities.isFlying = true;
                command(x.mc, String.format("/tp @s %d %d %d 0 40", matchedOrigin.getX() + 12, matchedOrigin.getY() + 9, matchedOrigin.getZ() - 9));
            });
            seq.until("the camera at the moved copy", 200, 15, x -> {
                keepMoving(matched, matchedOrigin, 6, 7, tick).ready(x);
                return Math.abs(x.mc.player.posZ - (matchedOrigin.getZ() - 9)) < 1.5 && Math.abs(x.mc.player.rotationPitch - 40) < 1;
            }).once(x -> {
                List<RenderChunkSchematicVbo> drawn = renderer().perf$lastDrawList();
                List<RenderChunkSchematicVbo> kept = CompleteCopy.keptRenderers();
                int quads = solidQuads(kept);
                int keptDrawn = 0, ghostsDrawn = 0;
                for (RenderChunkSchematicVbo p : drawn) {
                    if (kept.contains(p)) keptDrawn++;
                    if (GhostMove.renderers().contains(p)) ghostsDrawn++;
                }
                System.out.println("PERF movefull matched_quads=" + quads + " pieces=" + kept.size() + " drawn=" + keptDrawn + " ghosts_drawn=" + ghostsDrawn);
                x.check(GhostMove.isActive(), "the move ended");
                x.check(quads >= 432, "the complete copy holds " + quads + " quads of the 432 matching stone blocks' outer faces (every block of the schematic must be in it)");
                x.check(keptDrawn == kept.size() && keptDrawn > 0, keptDrawn + " of " + kept.size() + " complete pieces were drawn while moving");
                x.check(ghostsDrawn == 0, ghostsDrawn + " ghosts were drawn where the complete copy already shows the blocks");
                x.check(PerfStats.completeBuilds() > 0, "no complete piece was built");
                shot(x.mc, "perf-movefull-moving-matched-blocks");
            });
        }, cleaned(seq));

        PerfTests.Seq stop = new PerfTests.Seq();
        r.waitFor("perf/movefull/swap-back-on-stop-with-overlay-colours", 3000, c -> {
            c.check(matched != null && CompleteCopy.isKeptFor(matched), "no moved placement with a complete copy from the earlier test");
            stop.until("the move to settle and the normal pieces to come back", 2000, 1, x -> !GhostMove.isActive()).ticks(1).once(x -> {
                List<RenderChunkSchematicVbo> drawn = renderer().perf$lastDrawList();
                int complete = 0, overlay = 0;
                for (RenderChunkSchematicVbo p : drawn) {
                    if (((PerfChunk) p).perf$isComplete()) complete++;
                    if (p.hasOverlay()) overlay++;
                }
                x.check(complete == 0, complete + " complete pieces are still drawn after the move");
                x.check(drawn.size() > 0, "nothing is drawn after the move");
                x.check(CompleteCopy.isKeptFor(matched), "the complete copy was thrown away although the placement is still selected");
            }).until("the overlay colours of the moved (now mismatching) placement", 2000, 5, x -> {
                int overlay = 0;
                for (RenderChunkSchematicVbo p : renderer().perf$lastDrawList()) if (p.hasOverlay()) overlay++;
                return overlay > 0 && idle().ready(x);
            }).once(x -> shot(x.mc, "perf-movefull-after-move-overlay"));
        }, cleaned(stop));

        PerfTests.Seq again = new PerfTests.Seq();
        int[] made = new int[1];
        r.waitFor("perf/movefull/kept-for-the-next-move-until-deselected", 9000, c -> {
            c.check(matched != null && CompleteCopy.isKeptFor(matched), "no kept complete copy from the earlier test");
            made[0] = CompleteCopy.made();
            long builtBefore = PerfStats.completeBuilds();
            BlockPos now = matched.getPosition();
            manager().setOrigin(matched, new BlockPos(now.getX() + 2, now.getY(), now.getZ()));
            c.check(GhostMove.isActive(), "the second move did not start");
            c.check(CompleteCopy.made() == made[0] && CompleteCopy.isKeptFor(matched), "the second move made a new complete copy instead of using the kept one");
            again.ticks(1).once(x -> {
                int keptDrawn = 0;
                List<RenderChunkSchematicVbo> kept = CompleteCopy.keptRenderers();
                for (RenderChunkSchematicVbo p : renderer().perf$lastDrawList()) if (kept.contains(p)) keptDrawn++;
                x.check(keptDrawn == kept.size() && keptDrawn > 0, "the kept copy was not shown at once on the next move (" + keptDrawn + " of " + kept.size() + ")");
                x.check(PerfStats.completeBuilds() == builtBefore, "complete pieces were built again for the second move");
            }).until("the second move to settle", 2000, 1, x -> !GhostMove.isActive()).once(x -> {
                manager().setSelectedSchematicPlacement(null);
            }).ticks(2).once(x -> {
                x.check(CompleteCopy.exists() == false, "the complete copy was kept after the placement was deselected");
                removeAllPlacements();
                command(x.mc, String.format("/fill %d %d %d %d %d %d minecraft:air", matchedOrigin.getX(), matchedOrigin.getY(), matchedOrigin.getZ(),
                        matchedOrigin.getX() + 11, matchedOrigin.getY() + 2, matchedOrigin.getZ() + 11));
            });
            // the big schematic: how much memory its complete copy really takes
            bigSettled(c, again, "selftest-perf-movefull-big");
            int[] t = {0};
            BlockPos[] from = new BlockPos[1];
            again.once(x -> {
                SchematicPlacement p = manager().getAllSchematicPlacements().get(0);
                manager().setSelectedSchematicPlacement(p);
                from[0] = p.getPosition();
            }).until("the big complete copy", 3000, 1, x -> {
                SchematicPlacement p = manager().getAllSchematicPlacements().get(0);
                keepMoving(p, from[0], 3, 4, t).ready(x);
                return CompleteCopy.isKeptFor(p) && CompleteCopy.isBuilding() == false;
            }).once(x -> {
                bigCopyMb = CompleteCopy.usedBytes() / (1024.0 * 1024.0);
                System.out.println(String.format("PERF movefull_extra_mb=%.1f pieces=%d of %d budget_mb=%.0f", bigCopyMb, CompleteCopy.keptCount(), CompleteCopy.pieceCount(),
                        CompleteCopy.budgetBytes() / (1024.0 * 1024.0)));
                x.check(CompleteCopy.keptCount() > 0, "the big complete copy kept no piece");
                shot(x.mc, "perf-movefull-big-moving");
            }).until("the big move to settle", 3000, 1, x -> !GhostMove.isActive());
        }, cleaned(again));
    }

    // ------------------------------------------------------------------------------------------------ budget

    private static void registerBudget(Runner.Registry r) {
        PerfTests.Seq seq = new PerfTests.Seq();
        int[] t = {0};
        BlockPos[] from = new BlockPos[1];
        r.waitFor("perf/budget/tiny-limit-respected-nearest-first", 9000, c -> {
            c.check(bigCopyMb > 0, "no size of the big complete copy from the earlier test");
            SchematicPlacement p = manager().getAllSchematicPlacements().isEmpty() ? null : manager().getAllSchematicPlacements().get(0);
            c.check(p != null, "the big placement of the earlier test is missing");
            manager().setSelectedSchematicPlacement(null);
            manager().setSelectedSchematicPlacement(p);
            int limitMb = Math.max(1, (int) (bigCopyMb / 3));
            PerfConfigs.FULL_COPY_BUDGET_MB.setIntegerValue(limitMb);
            from[0] = p.getPosition();
            seq.until("the complete copy under the small limit", 3000, 1, x -> {
                keepMoving(p, from[0], 2, 3, t).ready(x);
                return CompleteCopy.isKeptFor(p) && CompleteCopy.isBuilding() == false;
            }).once(x -> {
                double[] d = CompleteCopy.keptVsOtherDistance();
                System.out.println(String.format("PERF budget_small limit_mb=%d used_mb=%.2f kept=%d rejected=%d max_kept_dist=%.1f min_other_dist=%.1f", limitMb,
                        CompleteCopy.usedBytes() / (1024.0 * 1024.0), CompleteCopy.keptCount(), CompleteCopy.rejectedCount(), d[0], d[1]));
                x.check(CompleteCopy.budgetBytes() == limitMb * 1024L * 1024L, "the slider's limit was not used: " + CompleteCopy.budgetBytes());
                x.check("slider".equals(CompleteCopy.budgetSource()), "limit source " + CompleteCopy.budgetSource());
                x.check(CompleteCopy.usedBytes() <= CompleteCopy.budgetBytes(), "the copy uses " + CompleteCopy.usedBytes() + " bytes, over the limit " + CompleteCopy.budgetBytes());
                x.check(CompleteCopy.budgetHit(), "a third of the copy's size did not stop the copy");
                x.check(CompleteCopy.keptCount() > 0 && CompleteCopy.rejectedCount() > 0, "kept " + CompleteCopy.keptCount() + ", left out " + CompleteCopy.rejectedCount());
                x.check(!(d[0] > d[1]), "a farther piece (" + d[0] + ") was kept while a nearer one (" + d[1] + ") was left out");
                // where the copy has no piece, the old ghost is drawn
                int ghosts = 0;
                for (RenderChunkSchematicVbo g : renderer().perf$lastDrawList()) if (GhostMove.renderers().contains(g)) ghosts++;
                x.check(ghosts > 0, "no ghost is drawn where the limited copy has no piece");
            }).until("the move to settle", 3000, 1, x -> !GhostMove.isActive()).once(x -> PerfConfigs.FULL_COPY_BUDGET_MB.setIntegerValue(0));
        }, cleaned(seq));

        r.add("perf/budget/auto-limit-from-free-graphics-memory", c -> {
            PerfConfigs.FULL_COPY_BUDGET_MB.setIntegerValue(0);
            long nvx = GpuMemory.nvxFreeKb(), ati = nvx > 0 ? -1 : GpuMemory.atiFreeKb();
            long expected = PerfMath.copyBudgetBytes(0, nvx, ati);
            long got = CompleteCopy.budgetNow();
            String source = CompleteCopy.budgetSource();
            System.out.println(String.format("PERF budget_mb=%.0f source=%s", got / (1024.0 * 1024.0), source));
            c.check(source.equals(PerfMath.copyBudgetSource(0, nvx, ati)), "source " + source + " for nvx=" + nvx + " ati=" + ati);
            c.check(Arrays.asList("nvx", "ati", "fixed").contains(source), "unknown source " + source);
            if (source.equals("fixed")) c.check(got == PerfMath.FIXED_BUDGET_BYTES, "the fixed limit is " + got);
            else c.check(Math.abs(got - expected) <= expected / 10, "the automatic limit " + got + " is not a quarter of the free graphics memory (" + expected + ")");
            c.check(got > 0, "the limit is 0");
        });

        PerfTests.Seq low = new PerfTests.Seq();
        int[] lt = {0};
        int[] counts = new int[2];
        r.waitFor("perf/budget/low-java-memory-skips-or-drops-the-copy", 6000, c -> {
            SchematicPlacement p = manager().getAllSchematicPlacements().isEmpty() ? null : manager().getAllSchematicPlacements().get(0);
            c.check(p != null, "the big placement of the earlier test is missing");
            from[0] = p.getPosition();
            low.until("a complete copy to drop", 3000, 1, x -> {
                keepMoving(p, from[0], 1, 2, lt).ready(x);
                return CompleteCopy.isKeptFor(p) && CompleteCopy.keptCount() > 0;
            }).once(x -> {
                counts[0] = CompleteCopy.droppedTight();
                counts[1] = CompleteCopy.skippedLowMemory();
                Runtime rt = Runtime.getRuntime();
                long used = rt.totalMemory() - rt.freeMemory();
                MemoryBudget.setMaxMemoryForTest(used + used / 10);
                keepMoving(p, from[0], 1, 2, lt).ready(x);
            }).until("the complete copy to be dropped on tight Java memory", 100, 3, x -> {
                // steps that are ready at once all run in one tick: keep moving over real frames (the copy is checked per frame)
                keepMoving(p, from[0], 1, 2, lt).ready(x);
                return CompleteCopy.exists() == false;
            }).once(x -> {
                x.check(CompleteCopy.exists() == false, "the complete copy was not dropped when Java memory got tight");
                x.check(CompleteCopy.droppedTight() > counts[0], "the drop was not counted");
                x.check(GhostMove.isActive(), "the move stopped");
                int ghosts = 0;
                for (RenderChunkSchematicVbo g : renderer().perf$lastDrawList()) if (GhostMove.renderers().contains(g)) ghosts++;
                x.check(ghosts > 0, "the ghosts did not take over from the dropped copy");
            }).until("the move to settle", 3000, 1, x -> !GhostMove.isActive()).once(x -> {
                // memory still low: a new move does not make a copy at all (the low limit is set again from what is used
                // now: dropping the copy freed memory)
                Runtime rt = Runtime.getRuntime();
                long used = rt.totalMemory() - rt.freeMemory();
                MemoryBudget.setMaxMemoryForTest(used + used / 10);
                manager().setOrigin(p, new BlockPos(from[0].getX() + 5, from[0].getY(), from[0].getZ()));
                x.check(GhostMove.isActive(), "the move did not start");
                x.check(CompleteCopy.exists() == false && CompleteCopy.skippedLowMemory() > counts[1], "a complete copy was made although Java memory was low");
                MemoryBudget.setMaxMemoryForTest(0);
            }).until("the move to settle", 3000, 1, x -> !GhostMove.isActive()).once(x -> {
                // the setting off: no copy either
                PerfConfigs.MOVE_FULL_COPY.setBooleanValue(false);
                manager().setOrigin(p, new BlockPos(from[0].getX() + 7, from[0].getY(), from[0].getZ()));
                x.check(GhostMove.isActive() && CompleteCopy.exists() == false, "a complete copy was made with the setting off");
                PerfConfigs.MOVE_FULL_COPY.setBooleanValue(true);
            }).until("the move to settle", 3000, 1, x -> !GhostMove.isActive()).once(x -> removeAllPlacements());
        }, c -> {
            try {
                return low.run(c);
            } catch (Throwable e) {
                cleanUp(c.mc);
                throw e;
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ VBOs off

    private static void registerVbo(Runner.Registry r) {
        PerfTests.Seq seq = new PerfTests.Seq();
        int[] t = {0};
        BlockPos[] from = new BlockPos[1];
        long[] m = new long[6];
        r.waitFor("perf/vbo/everything-works-with-vbos-off", 9000, c -> {
            Runner.dismissPopups(c.mc);
            removeAllPlacements();
            resetSettings();
            PerfGuard.resetForTest();
            c.mc.gameSettings.useVbo = false;
            c.mc.renderGlobal.loadRenderers();
            c.check(RenderWrap.useVbo() == false, "VBOs are still on");
            bigSettled(c, seq, "selftest-perf-vbo");
            measure(seq, 20, m);
            seq.once(x -> {
                x.check(renderer().perf$renderedWithData() > 0, "no piece holds built data with VBOs off");
                x.check(perFrame(m, 2) > 0, "nothing was drawn with VBOs off");
                x.check(PerfGuard.isOff(PerfGuard.Feature.DISPLAY_LISTS) == false, "the display-list upload failed");
                x.check(BatchDraw.active() == false, "batching claims to work without VBOs");
                shot(x.mc, "perf-vbo-off-big-placement");
                SchematicPlacement p = manager().getAllSchematicPlacements().get(0);
                manager().setSelectedSchematicPlacement(p);
                from[0] = p.getPosition();
            }).until("the complete copy with VBOs off", 3000, 1, x -> {
                SchematicPlacement p = manager().getAllSchematicPlacements().get(0);
                keepMoving(p, from[0], 4, 5, t).ready(x);
                return CompleteCopy.isKeptFor(p) && CompleteCopy.isBuilding() == false && CompleteCopy.keptCount() > 0;
            }).once(x -> {
                x.check(renderer().perf$lastDrawList().size() > 0, "nothing is drawn while moving with VBOs off");
                shot(x.mc, "perf-vbo-off-moving");
            }).until("the move to settle", 3000, 1, x -> !GhostMove.isActive()).until("the rebuild after the move", 3000, 10, idle()).once(x -> {
                x.check(renderer().perf$renderedWithData() > 0, "nothing is built after the move with VBOs off");
                x.check(PerfGuard.isOff(PerfGuard.Feature.DISPLAY_LISTS) == false, "the display-list upload failed");
                removeAllPlacements();
                x.mc.gameSettings.useVbo = true;
                x.mc.renderGlobal.loadRenderers();
            }).ticks(20);
        }, c -> {
            try {
                return seq.run(c);
            } catch (Throwable e) {
                cleanUp(c.mc);
                throw e;
            }
        });
    }

    // ------------------------------------------------------------------------------------------------ OptiFine (own phase)

    private static boolean optifineLoaded() {
        for (String name : new String[] {"Config", "net.optifine.Config", "optifine.OptiFineForgeTweaker", "optifine.Installer"}) {
            try {
                Class.forName(name, false, PerfDrawTests.class.getClassLoader());
                return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static void registerOptifine(Runner.Registry r) {
        r.add("perf/optifine/game-loads-with-optifine", c -> {
            c.check(optifineLoaded(), "OptiFine is not loaded in this game");
            c.check(renderer() != null, "Litematica's schematic renderer is missing");
            c.check(c.mc.world != null && c.mc.player != null, "not in a world");
            for (PerfGuard.Feature f : PerfGuard.Feature.values()) c.check(PerfGuard.isOff(f) == false, f + " was switched off by the fail-safe");
            System.out.println("PERF optifine loaded=true");
        });

        PerfTests.Seq seq = new PerfTests.Seq();
        int[] t = {0};
        BlockPos[] from = new BlockPos[1];
        long[] m = new long[6];
        r.waitFor("perf/optifine/big-placement-draws-and-moves", 9000, c -> {
            bigSettled(c, seq, "selftest-perf-optifine");
            measure(seq, 20, m);
            seq.once(x -> {
                x.check(renderer().perf$renderedWithData() > 0, "no piece holds built data with OptiFine");
                x.check(perFrame(m, 2) > 0, "nothing was drawn with OptiFine");
                shot(x.mc, "perf-optifine-big-placement");
                SchematicPlacement p = manager().getAllSchematicPlacements().get(0);
                manager().setSelectedSchematicPlacement(p);
                from[0] = p.getPosition();
            }).until("a move with OptiFine", 2000, 1, x -> {
                SchematicPlacement p = manager().getAllSchematicPlacements().get(0);
                keepMoving(p, from[0], 3, 4, t).ready(x);
                return t[0] > 40 && GhostMove.isActive() && CompleteCopy.isBuilding() == false;
            }).once(x -> {
                x.check(renderer().perf$lastDrawList().size() > 0, "nothing is drawn while moving with OptiFine");
                shot(x.mc, "perf-optifine-moving");
            }).until("the move to settle", 3000, 1, x -> !GhostMove.isActive()).until("the rebuild after the move", 3000, 10, idle()).once(x -> {
                x.check(renderer().perf$renderedWithData() > 0, "nothing is built after the move with OptiFine");
                for (PerfGuard.Feature f : PerfGuard.Feature.values()) x.check(PerfGuard.isOff(f) == false, f + " was switched off by the fail-safe");
                removeAllPlacements();
            });
        }, cleaned(seq));
    }
}
