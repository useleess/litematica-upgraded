package com.useleess.litematicaupgraded.compat;

import org.apache.logging.log4j.LogManager;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * masa's own LiteLoader Litematica next to ours (Discord #2): ours replaces it, and the hooks both take are skipped on our
 * side ({@link CompatMixinPlugin}), so some of ours may not work. The player is told once per game, on joining a world,
 * to remove his; malilib, MiniHUD, Tweakeroo and Item Scroller stay.
 */
public final class MasaLitematicaNotice {
    public static final MasaLitematicaNotice INSTANCE = new MasaLitematicaNotice();
    public static final String MESSAGE_KEY = "litematica_upgraded.message.masa_litematica_installed";
    /** For the in-game tests: how many times the notice was shown. */
    public static int shown;
    private boolean done;

    private MasaLitematicaNotice() {}

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.world == null || mc.ingameGUI == null) return;
        done = true;
        if (!CompatMixinPlugin.masaLitematicaPresent()) return;
        LogManager.getLogger("Litematica Upgraded").warn("masa's Litematica is installed next to Litematica Upgraded, which replaces it - the player was told to remove it");
        mc.ingameGUI.getChatGUI().printChatMessage(new TextComponentTranslation(MESSAGE_KEY));
        shown++;
    }
}
