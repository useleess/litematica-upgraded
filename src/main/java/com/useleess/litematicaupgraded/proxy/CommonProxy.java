package com.useleess.litematicaupgraded.proxy;

import java.io.File;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import com.useleess.litematicaupgraded.LitematicaUpgraded;
import com.useleess.litematicaupgraded.server.AccuratePlacement;
import com.useleess.litematicaupgraded.server.ServerConfig;
import com.useleess.litematicaupgraded.server.ServerEvents;
import com.useleess.litematicaupgraded.server.ServerJobs;
import com.useleess.litematicaupgraded.server.net.Net;

/** What runs on both sides: the server options, the network channel and the server part of the printer. */
public class CommonProxy {
    public void preInit(FMLPreInitializationEvent event) {
        ServerConfig.load(new File(event.getModConfigurationDirectory(), LitematicaUpgraded.MOD_ID + "-server.cfg"));
    }

    public void init(FMLInitializationEvent event) {
        Net.init();
        MinecraftForge.EVENT_BUS.register(AccuratePlacement.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ServerEvents.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ServerJobs.INSTANCE);
    }
}
