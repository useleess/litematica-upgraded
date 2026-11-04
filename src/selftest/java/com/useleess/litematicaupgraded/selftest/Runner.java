package com.useleess.litematicaupgraded.selftest;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.shader.Framebuffer;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Runs the tests, one step per client tick (so the game keeps ticking), printing the protocol tools/ingame_verdict.py
 * reads: {@code SCOPE-TEST total N}, then one {@code SCOPE-TEST pass|fail|skip <id> [:: reason]} per test, then
 * {@code SCOPE-TEST done}, after which the game quits.
 *
 * Pop-up guard: before each test every open screen is closed, so a leftover pop-up never blocks the next test.
 * Watchdog: a waiting test that is not ready after its tick limit fails with "timed out".
 */
public final class Runner {
    static final String FILTER = System.getProperty("litematicaupgraded.selftest.filter", "");
    /** The run's phase: "plain" (a server without the mod: every test but the server ones that need the mod) or "forge" (a Forge server with the mod: the server/ tests). */
    static final String PHASE = System.getProperty("litematicaupgraded.selftest.phase", "plain");
    /** The tests that need the server part: they run in the "forge" phase only. server/fallback/ runs in the plain one. */
    private static final String[] FORGE_ONLY = {"server/load/", "server/admin/", "server/accurate/", "server/air/", "server/range/", "server/shulker/",
        "server/save/", "server/paste/", "server/edit/", "server/move/", "server/permission/", "server/limits/"};
    /** Tests under a forge-only prefix that still run in the plain phase (a server without the mod): the fallback of Litematica's own paste. */
    private static final String[] PLAIN_TOO = {"server/permission/fallback-"};
    /** The tests of the "optifine" phase (a plain server, OptiFine HD U G5 in the client). */
    static final String OPTIFINE_PREFIX = "perf/optifine/";
    /** The tests of the "liteloader" phase (a plain server, Forge + LiteLoader and no MixinBooter in the client). */
    static final String LITELOADER_PREFIX = "compat/liteloader/";
    /** The tests of the "liteloader-first" phase (LiteLoader started before Forge, OptiFine, masa's mods: a player's setup, Discord #2). */
    static final String LITELOADER_FIRST_PREFIX = "compat/liteloader-first/";
    /** The tests of the "liteloader-optifine" phase (Forge first, LiteLoader after it, OptiFine, masa's mods: the Discord #4 setup). */
    static final String LITELOADER_OPTIFINE_PREFIX = "compat/liteloader-optifine/";
    /** The tests of the "liteloader-alone" phase (Forge + LiteLoader, none of masa's mods, no OptiFine). */
    static final String LITELOADER_ALONE_PREFIX = "compat/liteloader-alone/";
    /** The tests of the "uc" phase (a real Useleess Carpet server, its LU rules on at the start). */
    static final String UC_PREFIX = "uc/";
    private static final int JOIN_TICKS = 20 * 600;
    private static final int SETTLE_TICKS = 60;

    /** A test step: throw to fail. */
    public interface Body {
        void run(Context c) throws Exception;
    }

    /** A waiting test's readiness check, run once per tick until it returns true. */
    public interface Poll {
        boolean ready(Context c) throws Exception;
    }

    /** What the tests get to work with. */
    public static final class Context {
        final Minecraft mc = Minecraft.getMinecraft();

        public void check(boolean ok, String message) {
            if (!ok) throw new AssertionError(message);
        }
    }

    /** One test: a body (run at once) or a setup body followed by a poll with a tick limit. */
    static final class Test {
        final String id;
        final Body setup;
        final Poll poll;
        final int maxTicks;

        Test(String id, Body setup, Poll poll, int maxTicks) {
            this.id = id;
            this.setup = setup;
            this.poll = poll;
            this.maxTicks = maxTicks;
        }
    }

    /** Where the test groups add their tests. */
    public static final class Registry {
        final List<Test> tests = new ArrayList<>();

        public void add(String id, Body body) {
            tests.add(new Test(id, body, null, 0));
        }

        public void waitFor(String id, int maxTicks, Body setup, Poll poll) {
            tests.add(new Test(id, setup, poll, maxTicks));
        }
    }

    private final Registry registry = new Registry();
    private final Context context = new Context();
    private int waiting, settled, next, ticks, passed, failed;
    private boolean started, done;
    private Test current;

    public Runner() {
        Tests.register(registry);
    }

    /** Whether a test belongs to this run's phase. */
    static boolean inPhase(String id, String phase) {
        if ("uc".equals(phase) || id.startsWith(UC_PREFIX)) return "uc".equals(phase) && id.startsWith(UC_PREFIX);
        // the OptiFine phase (a plain server, OptiFine in the client) runs only its own short tests, and only there
        if ("liteloader-optifine".equals(phase) || id.startsWith(LITELOADER_OPTIFINE_PREFIX)) return "liteloader-optifine".equals(phase) && id.startsWith(LITELOADER_OPTIFINE_PREFIX);
        if ("liteloader-alone".equals(phase) || id.startsWith(LITELOADER_ALONE_PREFIX)) return "liteloader-alone".equals(phase) && id.startsWith(LITELOADER_ALONE_PREFIX);
        if ("liteloader-first".equals(phase) || id.startsWith(LITELOADER_FIRST_PREFIX)) return "liteloader-first".equals(phase) && id.startsWith(LITELOADER_FIRST_PREFIX);
        if ("liteloader".equals(phase) || id.startsWith(LITELOADER_PREFIX)) return "liteloader".equals(phase) && id.startsWith(LITELOADER_PREFIX);
        if ("optifine".equals(phase) || id.startsWith(OPTIFINE_PREFIX)) return "optifine".equals(phase) && id.startsWith(OPTIFINE_PREFIX);
        boolean forgeOnly = false;
        for (String prefix : FORGE_ONLY) forgeOnly |= id.startsWith(prefix);
        for (String prefix : PLAIN_TOO) if (id.startsWith(prefix)) forgeOnly = false;
        return "forge".equals(phase) == forgeOnly;
    }

    /** The watchdog's rule: a waiting test that used more ticks than its limit is timed out. */
    public static String timedOut(int ticks, int maxTicks) {
        return ticks > maxTicks ? "timed out after " + maxTicks + " ticks" : null;
    }

    /** Closes every open screen (the pop-up guard). */
    public static void dismissPopups(Minecraft mc) {
        if (mc.currentScreen != null) mc.displayGuiScreen((GuiScreen) null);
    }

    /** Saves what is on screen now as selftest-shots/&lt;name&gt;.png in the game folder. */
    public static void shot(Minecraft mc, String name) throws Exception {
        Framebuffer fb = mc.getFramebuffer();
        int w = fb.framebufferWidth, h = fb.framebufferHeight;
        ByteBuffer pixels = BufferUtils.createByteBuffer(w * h * 4);
        fb.bindFramebuffer(true);
        GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
        fb.unbindFramebuffer();
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4; // GL rows run bottom-up
                int rgb = (pixels.get(i) & 0xFF) << 16 | (pixels.get(i + 1) & 0xFF) << 8 | (pixels.get(i + 2) & 0xFF);
                image.setRGB(x, y, rgb);
            }
        }
        File dir = new File("selftest-shots").getAbsoluteFile();
        dir.mkdirs();
        ImageIO.write(image, "png", new File(dir, name + ".png"));
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) return;
        Minecraft mc = Minecraft.getMinecraft();
        // the game only mines / clicks while its window has focus; on the shared test box other games' windows take it
        // (2026-10-29: printer/mining saw 0 mining ticks), so the test game always counts as focused (no mouse grab)
        if (mc.world != null && mc.currentScreen == null) mc.inGameHasFocus = true;
        try {
            if (!started) {
                if (mc.currentScreen instanceof GuiDisconnected) {
                    System.out.println("SCOPE-TEST fail join :: disconnected from the test server");
                    finish(mc);
                    return;
                }
                boolean inWorld = mc.player != null && mc.world != null;
                if (!inWorld) {
                    if (++waiting > JOIN_TICKS) throw new IllegalStateException("the client did not get into the world");
                    return;
                }
                if (++settled < SETTLE_TICKS) return;
                started = true;
                registry.tests.removeIf(t -> !inPhase(t.id, PHASE) || !FILTER.isEmpty() && java.util.Arrays.stream(FILTER.split(",")).noneMatch(t.id::startsWith));
                System.out.println("SCOPE-TEST total " + registry.tests.size());
            }
            step(mc);
        } catch (Throwable e) {
            System.out.println("SCOPE-TEST fail harness/runner :: " + e);
            finish(mc);
        }
    }

    private void step(Minecraft mc) {
        if (current == null) {
            if (next >= registry.tests.size()) {
                finish(mc);
                return;
            }
            current = registry.tests.get(next++);
            ticks = 0;
            dismissPopups(mc);
            try {
                if (current.setup != null) current.setup.run(context);
            } catch (Throwable e) {
                end(e);
                return;
            }
            if (current.poll == null) end(null);
            return;
        }
        ticks++;
        try {
            if (current.poll.ready(context)) {
                end(null);
                return;
            }
        } catch (Throwable e) {
            end(e);
            return;
        }
        String late = timedOut(ticks, current.maxTicks);
        if (late != null) end(new AssertionError(late));
    }

    private void end(Throwable failure) {
        if (failure == null) {
            System.out.println("SCOPE-TEST pass " + current.id);
            passed++;
        } else {
            System.out.println("SCOPE-TEST fail " + current.id + " :: " + failure);
            // where an unexpected error came from (an assertion's message already says what is wrong)
            if (!(failure instanceof AssertionError)) {
                StackTraceElement[] trace = failure.getStackTrace();
                for (int i = 0; i < Math.min(8, trace.length); i++) System.out.println("  at " + trace[i]);
            }
            failed++;
        }
        current = null;
    }

    private void finish(Minecraft mc) {
        done = true;
        System.out.println("SCOPE TESTS: " + passed + " passed, " + failed + " failed");
        Tests.cleanup(mc);
        System.out.println("SCOPE-TEST done");
        mc.shutdown();
    }
}
