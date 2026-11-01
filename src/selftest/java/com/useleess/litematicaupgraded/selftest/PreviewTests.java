package com.useleess.litematicaupgraded.selftest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;

import com.useleess.litematicaupgraded.litematica.gui.SchematicBrowserScreen;
import com.useleess.litematicaupgraded.litematica.util.LitematicaDirectories;
import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntry;
import com.useleess.litematicaupgraded.preview.PreviewConfigs;
import com.useleess.litematicaupgraded.preview.PreviewMath;
import com.useleess.litematicaupgraded.preview.PreviewTiming;
import com.useleess.litematicaupgraded.preview.PreviewView;

/**
 * preview/: the turning 3D view in the schematic browser's info panel. The browser is opened and a file selected by name;
 * the numbers come from the view itself (frames drawn, vertices, angle, display lists held).
 */
final class PreviewTests {
    private static final String DIR_NAME = "lu-preview-tests";
    private static final PreviewView VIEW = PreviewView.INSTANCE;
    private static final int HOUSE_BLOCKS = 6 * 6 + 20 * 3 + 1;
    private static final int HUT_BLOCKS = 27;
    /** From selecting the file to the first picture, in milliseconds: the bars of the measurements (never looser). */
    private static final long SMALL_FIRST_MS = 300, DENSE_FIRST_MS = 500, DENSE_COMPLETE_MS = 3000, CACHED_FIRST_MS = 100;

    private static long frames;
    private static int stage, resident, cancelsBefore;
    private static float angle;
    private static boolean closed;
    private static long windowShotFrame;
    private static SchematicBrowserScreen screen;

    private PreviewTests() {}

    static void register(Runner.Registry r) {
        r.waitFor("preview/browser-builds-and-draws-the-model", 600, c -> open(c, house()), c -> {
            if (!VIEW.complete() || VIEW.framesDrawn() < 3) return false;
            c.check(VIEW.blockCount() == HOUSE_BLOCKS, "the model holds " + VIEW.blockCount() + " blocks, the file " + HOUSE_BLOCKS);
            c.check(VIEW.step() == 1, "a small file was shrunk to 1:" + VIEW.step());
            c.check(VIEW.textured(), "the model has no texture coordinates");
            c.check(VIEW.timing().firstFrameMs < SMALL_FIRST_MS, "the small file's first picture came after " + VIEW.timing().firstFrameMs + " ms");
            return true;
        });
        r.waitFor("preview/model-turns-between-frames", 600, c -> {
            open(c, house());
            frames = -1;
        }, c -> {
            if (VIEW.framesDrawn() < 3) return false;
            if (frames < 0) {
                frames = VIEW.framesDrawn();
                angle = VIEW.lastAngle();
                return false;
            }
            if (VIEW.framesDrawn() < frames + 5) return false;
            float turned = Math.abs(VIEW.lastAngle() - angle);
            c.check(turned > 0.5f, "the angle stayed at " + angle);
            return true;
        });
        r.waitFor("preview/screenshot-of-the-browser", 600, c -> open(c, house()), c -> {
            if (VIEW.framesDrawn() < 30) return false;
            Runner.shot(c.mc, "preview-browser");
            return true;
        });
        r.waitFor("preview/drag-turns-the-view-and-the-wheel-zooms", 600, c -> open(c, house()), c -> {
            if (VIEW.vertexCount() == 0 || VIEW.framesDrawn() < 3) return false;
            int[] view = VIEW.viewBounds();
            int x = view[0] + view[2] / 2, y = view[1] + view[3] / 2;
            float yaw = VIEW.lastAngle(), pitch = VIEW.pitch();
            c.check(screen.onMouseClicked(x, y, 0), "a click inside the view was not taken by it");
            VIEW.dragTo(x + 20, y - 10);
            screen.onMouseReleased(x + 20, y - 10, 0);
            c.check(Math.abs(VIEW.lastAngle() - yaw - 30f) < 0.5f, "dragging 20 pixels turned the view from " + yaw + " to " + VIEW.lastAngle());
            c.check(Math.abs(VIEW.pitch() - pitch + 15f) < 0.5f, "dragging up 10 pixels tilted the view from " + pitch + " to " + VIEW.pitch());
            VIEW.dragTo(x + 80, y + 80);
            c.check(Math.abs(VIEW.lastAngle() - yaw - 30f) < 0.5f, "the view kept turning after the button was released");
            c.check(VIEW.zoom() == 1f, "the zoom started at " + VIEW.zoom());
            c.check(screen.onMouseScrolled(x, y, 1, 0), "a scroll inside the view was not taken by it");
            c.check(Math.abs(VIEW.zoom() - 1.25f) < 0.001f, "scrolling in left the zoom at " + VIEW.zoom());
            c.check(!VIEW.scroll(view[0] - 1, y, -1), "a scroll outside the view zoomed it");
            c.check(Math.abs(VIEW.zoom() - 1.25f) < 0.001f, "a scroll outside the view changed the zoom to " + VIEW.zoom());
            return true;
        });
        r.waitFor("preview/windows-of-a-shrunk-wall-keep-their-glass", 2400, c -> { open(c, windowedWall()); windowShotFrame = 0; }, c -> {
            if (!VIEW.complete() || VIEW.framesDrawn() < 30) return false;
            c.check(VIEW.step() == 2, "the 270 block wide house was shrunk 1:" + VIEW.step());
            c.check(VIEW.translucentVertexCount() > 0, "the shrunk house shows no glass in its windows");
            if (windowShotFrame == 0) {
                VIEW.holdViewForTest(0f, 8f);
                windowShotFrame = VIEW.framesDrawn() + 5;
            }
            if (VIEW.framesDrawn() < windowShotFrame) return false;
            Runner.shot(c.mc, "preview-browser-windows");
            return true;
        });
        r.waitFor("preview/huge-file-is-shrunk-and-drawn-with-textures-without-long-frames", 2400, c -> open(c, huge()), c -> {
            if (!VIEW.complete() || VIEW.framesDrawn() < 10) return false;
            checkShrunk(c);
            return true;
        });
        r.waitFor("preview/big-schematic-is-shrunk-and-drawn-with-textures-without-long-frames", 2400, c -> open(c, big()), c -> {
            if (!VIEW.complete() || VIEW.framesDrawn() < 10) return false;
            checkShrunk(c);
            c.check(VIEW.step() == 2, "the 288 block wide schematic was shrunk 1:" + VIEW.step());
            return true;
        });
        r.waitFor("preview/screenshot-of-the-browser-with-a-big-schematic", 3600, c -> open(c, big()), c -> {
            if (!VIEW.complete() || !VIEW.detailComplete() || VIEW.framesDrawn() < 30) return false;
            Runner.shot(c.mc, "preview-browser-big");
            return true;
        });
        r.waitFor("preview/a-big-schematic-draws-the-full-model-at-the-default-zoom-and-when-zoomed-out", 3600, c -> open(c, big()), c -> {
            if (!VIEW.complete() || !VIEW.detailComplete() || VIEW.framesDrawn() < 10) return false;
            int[] view = VIEW.viewBounds();
            int x = view[0] + view[2] / 2, y = view[1] + view[3] / 2;
            c.check(VIEW.drawnStep() == 1, "at the default zoom the model drawn is 1:" + VIEW.drawnStep() + ", not the full one");
            c.check(VIEW.drawnVertexCount() == VIEW.detailVertexCount(), "the drawn model is not the full one");
            c.check(VIEW.detailVertexCount() > VIEW.vertexCount(), "the full model holds " + VIEW.detailVertexCount() + " vertices, the shrunk one "
                    + VIEW.vertexCount());
            for (int i = 0; i < 3; i++) c.check(screen.onMouseScrolled(x, y, -1, 0), "a scroll inside the view was not taken by it");
            c.check(VIEW.drawnStep() == 1, "zoomed out the model drawn is 1:" + VIEW.drawnStep() + ", not the full one");
            c.check(VIEW.maxDrawMillis() < 100, "a frame of the view took " + VIEW.maxDrawMillis() + " ms");
            return true;
        });
        r.waitFor("preview/screenshot-of-the-browser-zoomed-in-on-a-big-schematic", 3600, c -> { open(c, big()); windowShotFrame = 0; }, c -> {
            if (!VIEW.complete() || !VIEW.detailComplete() || VIEW.framesDrawn() < 10) return false;
            int[] view = VIEW.viewBounds();
            if (windowShotFrame == 0) {
                for (int i = 0; i < 12; i++) screen.onMouseScrolled(view[0] + view[2] / 2, view[1] + view[3] / 2, 1, 0);
                windowShotFrame = VIEW.framesDrawn() + 30;
            }
            if (VIEW.framesDrawn() < windowShotFrame) return false;
            Runner.shot(c.mc, "preview-browser-zoomed");
            return true;
        });
        r.waitFor("preview/a-schematic-over-the-size-cap-never-builds-the-full-model", 2400, c -> {
            PreviewView.capVerticesForTest(1000);
            open(c, big());
        }, c -> {
            try {
                if (!VIEW.complete() || !VIEW.detailSettled()) return false;
                int[] view = VIEW.viewBounds();
                for (int i = 0; i < 60; i++) screen.onMouseScrolled(view[0] + view[2] / 2, view[1] + view[3] / 2, 1, 0);
                c.check(!VIEW.detailComplete() && VIEW.detailVertexCount() == 0, "a full model was built over the vertex cap");
                c.check(VIEW.zoom() <= PreviewMath.ZOOM_MAX, "the zoom went past " + PreviewMath.ZOOM_MAX + " without a full model: " + VIEW.zoom());
                c.check(VIEW.drawnStep() == 2, "the model drawn is 1:" + VIEW.drawnStep() + " over the cap");
                return true;
            } finally {
                if (VIEW.detailSettled()) PreviewView.resetVertexCap();
            }
        });
        r.waitFor("preview/dense-file-shows-a-first-picture-fast-and-completes-fast", 2400, c -> open(c, dense()), c -> {
            if (!VIEW.complete() || VIEW.framesDrawn() < 5) return false;
            PreviewTiming timing = VIEW.timing();
            c.check(timing.firstFrameMs < DENSE_FIRST_MS, "the first picture of the 120 x 38 x 120 build came after " + timing.firstFrameMs + " ms");
            c.check(timing.completeMs < DENSE_COMPLETE_MS, "the 120 x 38 x 120 build was complete after " + timing.completeMs + " ms");
            c.check(timing.readMs >= 0 && timing.downsampleMs >= 0 && timing.buildMs >= 0 && timing.uploadMs >= 0, "a stage has no time: " + timing.readMs + "/"
                    + timing.downsampleMs + "/" + timing.buildMs + "/" + timing.uploadMs);
            c.check(VIEW.textured() && VIEW.maxDrawMillis() < 100, "the dense build drew a frame in " + VIEW.maxDrawMillis() + " ms");
            return true;
        });
        r.waitFor("preview/dense-file-comes-in-piece-by-piece-with-a-rough-copy-first", 2400, c -> open(c, dense()), c -> {
            if (!VIEW.complete()) return false;
            c.check(VIEW.timing().rough, "the first picture of a build over " + PreviewMath.ROUGH_BLOCKS + " blocks was not the rough copy");
            c.check(VIEW.blockCount() > PreviewMath.ROUGH_BLOCKS, "the complete model holds only " + VIEW.blockCount() + " blocks");
            c.check(VIEW.step() == 1, "the complete 120 block wide build was shrunk to 1:" + VIEW.step());
            return true;
        });
        r.waitFor("preview/going-back-to-a-file-draws-it-in-the-first-frame", 1200, c -> {
            hut();
            open(c, house());
            stage = 0;
        }, c -> {
            if (stage == 0) {
                if (!VIEW.complete()) return false;
                resident = VIEW.glResources();
                select(screen, hut());
                stage = 1;
            } else if (stage == 1) {
                if (!VIEW.complete() || VIEW.blockCount() != HUT_BLOCKS) return false;
                VIEW.resetStats();
                select(screen, house());
                stage = 2;
            } else {
                if (VIEW.framesDrawn() < 1) return false;
                c.check(VIEW.timing().fromCache, "the file was built again instead of coming from the kept previews");
                c.check(VIEW.complete() && VIEW.blockCount() == HOUSE_BLOCKS, "the first frame did not show the whole house");
                c.check(VIEW.timing().firstFrameMs < CACHED_FIRST_MS, "the kept preview came after " + VIEW.timing().firstFrameMs + " ms");
                c.check(VIEW.glResources() > resident, "the two kept previews do not both hold display lists");
                return true;
            }
            return false;
        });
        r.waitFor("preview/choosing-another-file-stops-the-build-of-the-old-one", 1200, c -> {
            hut();
            open(c, dense());
            cancelsBefore = PreviewView.cancelledBuilds();
            select(screen, hut());
        }, c -> {
            if (!VIEW.complete()) return false;
            c.check(PreviewView.cancelledBuilds() > cancelsBefore, "the build of the first file was not stopped");
            c.check(VIEW.blockCount() == HUT_BLOCKS, "the view shows " + VIEW.blockCount() + " blocks, not the hut's " + HUT_BLOCKS);
            return true;
        });
        r.waitFor("preview/setting-off-draws-nothing", 200, c -> {
            PreviewConfigs.ENABLED.setBooleanValue(false);
            open(c, house());
            frames = 0;
        }, c -> {
            try {
                if (++frames < 40) return false;
                c.check(VIEW.framesDrawn() == 0, "the view drew " + VIEW.framesDrawn() + " frames with the setting off");
                c.check(VIEW.vertexCount() == 0 && VIEW.glResources() == 0, "the view kept a model with the setting off");
                return true;
            } finally {
                if (frames >= 40) PreviewConfigs.ENABLED.setBooleanValue(true);
            }
        });
        r.waitFor("preview/closing-the-screen-frees-the-gl-resources", 600, c -> {
            open(c, house());
            closed = false;
        }, c -> {
            if (!closed) {
                if (VIEW.vertexCount() == 0 || VIEW.framesDrawn() < 3) return false;
                c.check(VIEW.glResources() > 0, "no display list while the view is drawn");
                c.mc.displayGuiScreen(null);
                closed = true;
                return false;
            }
            return VIEW.glResources() == 0 && VIEW.vertexCount() == 0;
        });
        r.waitFor("preview/unreadable-file-shows-no-preview", 400, c -> {
            Path bad = dir().resolve("preview-bad.litematic");
            Files.write(bad, new byte[] {1, 2, 3, 4, 5, 6, 7, 8});
            open(c, bad);
        }, c -> {
            if (VIEW.error() == null) return false;
            c.check(!VIEW.switchedOff(), "an unreadable file switched the view off");
            return true;
        });
        r.waitFor("preview/draw-error-switches-the-view-off", 400, c -> {
            open(c, house());
            VIEW.failNextDrawForTest(new IllegalStateException("test: graphics driver refused"));
        }, c -> {
            if (!VIEW.switchedOff()) return false;
            c.check(VIEW.glResources() == 0 && VIEW.vertexCount() == 0, "the failed view kept its model");
            VIEW.release();
            c.check(!VIEW.switchedOff(), "the view stays off after the screen is gone");
            return true;
        });
    }

    /** The shown model is a smaller copy made of real textured blocks, within the caps, drawn without long frames. */
    private static void checkShrunk(Runner.Context c) {
        c.check(VIEW.step() > 1, "the file was not shrunk (1:" + VIEW.step() + ")");
        c.check(VIEW.textured(), "the shrunk model has no texture coordinates");
        c.check(VIEW.blockCount() > 0 && VIEW.blockCount() <= PreviewMath.MAX_BLOCKS, "the shrunk model holds " + VIEW.blockCount() + " blocks");
        c.check(VIEW.maxDrawMillis() < 100, "a frame of the view took " + VIEW.maxDrawMillis() + " ms");
    }

    /** Removes the test files (they are in the player's schematics folder while the tests run). */
    static void cleanup() {
        VIEW.release();
        try {
            Path dir = LitematicaDirectories.getSchematicsBaseDirectory().resolve(DIR_NAME);
            if (Files.isDirectory(dir)) {
                try (Stream<Path> files = Files.list(dir)) {
                    for (Path file : (Iterable<Path>) files::iterator) Files.deleteIfExists(file);
                }
                Files.deleteIfExists(dir);
            }
        } catch (IOException | RuntimeException e) {
            System.out.println("preview/ cleanup: " + e);
        }
    }

    private static Path dir() throws IOException {
        Path dir = LitematicaDirectories.getSchematicsBaseDirectory().resolve(DIR_NAME);
        Files.createDirectories(dir);
        return dir;
    }

    /** A small house: a 6 x 6 floor, three rows of walls around it and one glass block. */
    private static Path house() throws IOException {
        Path file = dir().resolve("preview-house.litematic");
        if (Files.exists(file)) return file;
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<>();
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 6; z++) {
                blocks.put(new BlockPos(x, 0, z), Blocks.STONE.getDefaultState());
                boolean wall = x == 0 || z == 0 || x == 5 || z == 5;
                for (int y = 1; y <= 3 && wall; y++) blocks.put(new BlockPos(x, y, z), Blocks.PLANKS.getDefaultState());
            }
        }
        blocks.put(new BlockPos(3, 1, 3), Blocks.GLASS.getDefaultState());
        LitematicWriter.write(file, blocks);
        return file;
    }

    /** A 3 x 3 x 3 cube of cobblestone: a second small file to switch to. */
    private static Path hut() throws IOException {
        Path file = dir().resolve("preview-hut.litematic");
        if (Files.exists(file)) return file;
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<>();
        for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) for (int z = 0; z < 3; z++) blocks.put(new BlockPos(x, y, z), Blocks.COBBLESTONE.getDefaultState());
        LitematicWriter.write(file, blocks);
        return file;
    }

    /**
     * A 120 x 38 x 120 town: a grass floor with paths, and a hollow, windowed tower of mixed blocks on most 12 x 12 plots.
     * Under 200,000 blocks (so shown as it is) but over the size that gets a rough copy first.
     */
    private static Path dense() throws IOException {
        Path file = dir().resolve("preview-dense.litematic");
        if (Files.exists(file)) return file;
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<>();
        IBlockState[] kinds = {Blocks.COBBLESTONE.getDefaultState(), Blocks.PLANKS.getDefaultState(), Blocks.BRICK_BLOCK.getDefaultState(),
                Blocks.WOOL.getDefaultState(), Blocks.STONEBRICK.getDefaultState()};
        for (int x = 0; x < 120; x++) for (int z = 0; z < 120; z++) {
            boolean path = x % 12 < 2 || z % 12 < 2;
            blocks.put(new BlockPos(x, 0, z), path ? Blocks.GRAVEL.getDefaultState() : Blocks.GRASS.getDefaultState());
            int tx = x / 12, tz = z / 12;
            if (path || (tx * 3 + tz) % 7 == 0) continue;
            int height = 10 + (tx * 7 + tz * 5) % 28;
            IBlockState kind = kinds[(tx + tz) % kinds.length];
            boolean shell = x % 12 == 2 || x % 12 == 11 || z % 12 == 2 || z % 12 == 11;
            if (shell) {
                for (int y = 1; y < height; y++) blocks.put(new BlockPos(x, y, z), y % 4 == 2 && (x + z) % 2 == 0 ? Blocks.GLASS.getDefaultState() : kind);
            } else {
                blocks.put(new BlockPos(x, height, z), kind);
                blocks.put(new BlockPos(x, 1, z), Blocks.PLANKS.getDefaultState());
            }
        }
        LitematicWriter.write(file, blocks);
        return file;
    }

    /**
     * A 270 x 24 x 40 house: stone walls with plank bands, a plank roof, and big light blue glass windows (16 wide, 10 high) on all
     * four walls. Wider than 256 blocks, so it is shrunk to half size.
     */
    private static Path windowedWall() throws IOException {
        Path file = dir().resolve("preview-windows.litematic");
        if (Files.exists(file)) return file;
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<>();
        int sx = 270, sz = 40, wallTop = 17;
        IBlockState glass = Blocks.STAINED_GLASS.getStateFromMeta(3);
        for (int x = 0; x < sx; x++) for (int z = 0; z < sz; z++) {
            boolean xWall = z == 0 || z == sz - 1, zWall = x == 0 || x == sx - 1;
            blocks.put(new BlockPos(x, 0, z), Blocks.STONE.getDefaultState());
            if (xWall || zWall) {
                for (int y = 1; y <= wallTop; y++) {
                    boolean window = y >= 4 && y < 14 && (xWall ? x % 30 >= 7 && x % 30 < 23 && x > 0 && x < sx - 1 : z >= 12 && z < 28 && z > 0 && z < sz - 1);
                    blocks.put(new BlockPos(x, y, z), window ? glass : y == 1 || y == wallTop ? Blocks.PLANKS.getDefaultState() : Blocks.STONE.getDefaultState());
                }
            }
            int roof = Math.min(Math.min(x, sx - 1 - x) / 2, Math.min(z, sz - 1 - z));
            blocks.put(new BlockPos(x, wallTop + 1 + Math.min(roof, 5), z), Blocks.PLANKS.getDefaultState());
        }
        LitematicWriter.write(file, blocks);
        return file;
    }

    /** A solid 64 x 64 x 64 cube: more blocks than the preview shows, so it is shrunk. */
    private static Path huge() throws IOException {
        Path file = dir().resolve("preview-huge.litematic");
        if (Files.exists(file)) return file;
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<>();
        for (int x = 0; x < 64; x++) for (int y = 0; y < 64; y++) for (int z = 0; z < 64; z++) {
            blocks.put(new BlockPos(x, y, z), (x + y + z) % 7 == 0 ? Blocks.COBBLESTONE.getDefaultState() : Blocks.STONE.getDefaultState());
        }
        LitematicWriter.write(file, blocks);
        return file;
    }

    /**
     * A 288 x 40 x 288 build: a stone floor with a path of planks and gaps, towers of four block kinds with air gaps, glass,
     * and a hollow wall around the edge. Wider than 256 blocks, so it is shrunk to half size.
     */
    private static Path big() throws IOException {
        Path file = dir().resolve("preview-big.litematic");
        if (Files.exists(file)) return file;
        Map<BlockPos, IBlockState> blocks = new LinkedHashMap<>();
        IBlockState[] kinds = {Blocks.COBBLESTONE.getDefaultState(), Blocks.PLANKS.getDefaultState(), Blocks.BRICK_BLOCK.getDefaultState(),
                Blocks.WOOL.getDefaultState(), Blocks.GLASS.getDefaultState()};
        for (int x = 0; x < 288; x++) for (int z = 0; z < 288; z++) {
            boolean path = x % 24 < 3 || z % 24 < 3;
            blocks.put(new BlockPos(x, 0, z), path ? Blocks.PLANKS.getDefaultState() : Blocks.GRASS.getDefaultState());
            boolean edge = x < 2 || z < 2 || x >= 286 || z >= 286;
            for (int y = 1; y < 8 && edge; y++) if (y % 3 != 0 || (x + z) % 5 != 0) blocks.put(new BlockPos(x, y, z), kinds[0]);
            int tx = x / 24, tz = z / 24;
            boolean tower = x % 24 >= 6 && x % 24 < 18 && z % 24 >= 6 && z % 24 < 18;
            if (!tower || (tx + tz) % 3 == 2) continue;
            int height = 12 + (tx * 7 + tz * 5) % 28;
            IBlockState kind = kinds[1 + (tx + tz) % 4];
            for (int y = 1; y < height; y++) {
                boolean shell = x % 24 == 6 || x % 24 == 17 || z % 24 == 6 || z % 24 == 17;
                if (shell && y % 6 != 0) blocks.put(new BlockPos(x, y, z), kind);
            }
        }
        LitematicWriter.write(file, blocks);
        return file;
    }

    /** Opens the browser in the test folder and selects the file by name; the view's numbers start from zero. */
    private static void open(Runner.Context c, Path file) throws IOException {
        VIEW.release();
        VIEW.resetStats();
        SchematicBrowserScreen browser = new SchematicBrowserScreen();
        screen = browser;
        BaseScreen.openScreen(browser);
        browser.getListWidget().switchToDirectory(file.getParent());
        select(browser, file);
    }

    /** Clicks the file's row in the open browser, found by its name. */
    private static void select(SchematicBrowserScreen browser, Path file) {
        DirectoryEntry wanted = null;
        for (DirectoryEntry entry : browser.getListWidget().getFilteredDataList()) {
            if (entry.getName().equals(file.getFileName().toString())) wanted = entry;
        }
        if (wanted == null) throw new IllegalStateException("the browser does not list " + file.getFileName());
        browser.getListWidget().getEntrySelectionHandler().clickEntry(browser.getListWidget().getEntrySelectionHandler().getDataList().indexOf(wanted));
    }
}
