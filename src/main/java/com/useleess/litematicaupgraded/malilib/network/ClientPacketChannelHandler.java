package com.useleess.litematicaupgraded.malilib.network;

public interface ClientPacketChannelHandler
{
    void registerClientChannelHandler(PluginChannelHandler handler);

    void unregisterClientChannelHandler(PluginChannelHandler handler);
}