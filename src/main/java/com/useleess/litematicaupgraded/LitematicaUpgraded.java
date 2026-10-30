package com.useleess.litematicaupgraded;

import org.apache.logging.log4j.LogManager;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;

import com.useleess.litematicaupgraded.compat.MixinCheck;
import com.useleess.litematicaupgraded.proxy.CommonProxy;
import com.useleess.litematicaupgraded.server.PrinterCommand;

/**
 * Forge 1.12.2 build of masa's Litematica + malilib (LGPL-3.0), with Useleess additions. The same jar runs on a client
 * (the whole mod) and on a dedicated server (only the printer's server part); the client classes are only reached
 * through the client proxy. A client without the mod can join a server that has it, and the other way round.
 */
@Mod(modid = LitematicaUpgraded.MOD_ID, name = "Litematica Upgraded", useMetadata = true,
        acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.12.2]")
public class LitematicaUpgraded {
    public static final String MOD_ID = "litematica_upgraded";

    @SidedProxy(clientSide = "com.useleess.litematicaupgraded.proxy.ClientProxy", serverSide = "com.useleess.litematicaupgraded.proxy.ServerProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MixinCheck.require();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        proxy.init(event);
        LogManager.getLogger(MOD_ID).info("Litematica Upgraded ready ({})", event.getSide());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new PrinterCommand());
    }
}
