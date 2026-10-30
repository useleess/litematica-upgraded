// The client side of the server part's capabilities: applies the message, keeps the printer off while the server says so.
package com.useleess.litematicaupgraded.printer;

import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;

import com.useleess.litematicaupgraded.litematica.Litematica;
import com.useleess.litematicaupgraded.server.ServerFeatures;
import com.useleess.litematicaupgraded.server.net.MessageCapabilities;

public final class ServerFeaturesClient {
    public static final ServerFeaturesClient INSTANCE = new ServerFeaturesClient();

    /** The capabilities applied last (for tests and the debug screen); null while no server part has answered. */
    public static volatile MessageCapabilities last;
    /** How many times the printer was switched off because the server does not allow it (for tests). */
    public static volatile int blocked;
    /** The text of the last notice shown to the player because of the server's switch (for tests). */
    public static volatile String lastNotice = "";

    private ServerFeaturesClient() {}

    /** Runs before the printer's own tick (registered first): a new message takes effect, a printer the server forbids goes off at once. */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MessageCapabilities message = ServerFeatures.takePending();
        if (message != null) {
            last = message;
            Litematica.LOGGER.info("Server part: {}", message);
        }
        if (!ServerFeatures.printerAllowed && PrinterConfigs.PRINTER_ENABLED.getBooleanValue()) {
            // the hotkey or the toggle turned it on, or the server just switched it off
            PrinterConfigs.PRINTER_ENABLED.setBooleanValue(false);
            blocked++;
            Minecraft mc = Minecraft.getMinecraft();
            TextComponentTranslation notice = new TextComponentTranslation("litematica_upgraded.message.printer.disabled_by_server");
            lastNotice = notice.getUnformattedText();
            if (mc.player != null) mc.player.sendStatusMessage(notice, false);
        }
    }

    @SubscribeEvent
    public void onConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        ServerFeatures.reset();
        last = null;
    }

    @SubscribeEvent
    public void onDisconnected(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        ServerFeatures.reset();
        last = null;
    }
}
