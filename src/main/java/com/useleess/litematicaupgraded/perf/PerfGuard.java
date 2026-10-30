package com.useleess.litematicaupgraded.perf;

import java.util.EnumSet;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.util.text.TextFormatting;

import com.useleess.litematicaupgraded.litematica.Litematica;

/**
 * The fail-safe of the draw-side speed-ups: when one of them throws (a patch that does not fit this game, a graphics
 * driver that refuses something), it is switched off for the rest of the session, a chat note says so, and the game keeps
 * running with the stock path. Render thread only.
 */
public final class PerfGuard {
    public enum Feature {
        BATCH("perfBatchDraw"),
        TRANSLUCENT("perfLazyTranslucentSort"),
        FAR("perfFarDetail"),
        FULL_COPY("perfMoveFullCopy"),
        DISPLAY_LISTS("displayLists");

        final String configName;

        Feature(String configName) {
            this.configName = configName;
        }
    }

    private static final Set<Feature> OFF = EnumSet.noneOf(Feature.class);
    private static final Set<Feature> NOTE_PENDING = EnumSet.noneOf(Feature.class);
    private static int notesShown;

    private PerfGuard() {}

    public static boolean isOff(Feature f) {
        return OFF.contains(f);
    }

    /** Switches a feature off for the session after it failed; the note goes to chat on the next frame with a player. */
    public static void fail(Feature f, Throwable t) {
        if (OFF.add(f)) {
            Litematica.LOGGER.error("Litematica Upgraded: speed-up {} failed and is off for this session", f, t);
            NOTE_PENDING.add(f);
        }
    }

    /** Every frame: shows the pending notes. */
    public static void showNotes() {
        if (NOTE_PENDING.isEmpty()) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null) return;
        for (Feature f : NOTE_PENDING) {
            String name = "litematica_upgraded.config.name." + f.configName.toLowerCase();
            TextComponentTranslation msg = new TextComponentTranslation("litematica_upgraded.message.perf.feature_off", new TextComponentTranslation(name));
            msg.getStyle().setColor(TextFormatting.GOLD);
            mc.player.sendMessage(msg);
            notesShown++;
        }
        NOTE_PENDING.clear();
    }

    /** How many chat notes were shown (tests). */
    public static int notesShown() {
        return notesShown;
    }

    /** Tests: turns the switched-off features back on. */
    public static void resetForTest() {
        OFF.clear();
        NOTE_PENDING.clear();
    }
}
