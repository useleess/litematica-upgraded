// Tells clients what the server part supports, after Schematica Upgraded's PlayerHandler (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.useleess.litematicaupgraded.server.net.MessageCapabilities;
import com.useleess.litematicaupgraded.server.net.Net;

/** Sends the capabilities message to every player at join, and to all players again whenever an option changed. */
public final class ServerEvents {
    public static final ServerEvents INSTANCE = new ServerEvents();

    /** The message last sent to everybody; null until the first player joined. */
    private MessageCapabilities sent;

    private ServerEvents() {}

    /** What the server supports now, from the options. */
    public static MessageCapabilities capabilities() {
        MessageCapabilities message = new MessageCapabilities(ServerConfig.printerAllowed, ServerConfig.accuratePlacement, ServerConfig.airPlacement,
            ServerConfig.shulkerRestock, (float) ServerConfig.maxRange,
            ServerConfig.saving, ServerConfig.edits, ServerConfig.uploadKiB);
        message.accurateStates = ServerConfig.accuratePlacement;
        return message;
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.player instanceof EntityPlayerMP)) return;
        MessageCapabilities capabilities = capabilities();
        if (sent == null) sent = capabilities;
        Net.CHANNEL.sendTo(capabilities, (EntityPlayerMP) event.player);
    }

    /** An option changed while the server runs (the admin command): all connected clients are told within a tick. */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || sent == null) return;
        MessageCapabilities current = capabilities();
        if (current.equals(sent)) return;
        sent = current;
        Net.CHANNEL.sendToAll(current);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        AccuratePlacement.INSTANCE.forget(event.player);
        ServerJobs.INSTANCE.forget(event.player);
    }
}
