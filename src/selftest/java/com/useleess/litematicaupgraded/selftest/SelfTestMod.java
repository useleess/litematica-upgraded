package com.useleess.litematicaupgraded.selftest;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;

/**
 * The in-world tests, as a second mod next to Litematica Upgraded (a jar of its own, never in the release jar).
 * Does nothing unless the game runs with {@code -Dlitematicaupgraded.selftest=client} (tools/ingame.ps1 sets it).
 */
@Mod(modid = "litematica_upgraded_selftest", name = "Litematica Upgraded in-world tests", version = "1",
        clientSideOnly = true, acceptedMinecraftVersions = "[1.12.2]", dependencies = "required-after:litematica_upgraded")
public final class SelfTestMod {
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if ("client".equals(System.getProperty("litematicaupgraded.selftest")) && event.getSide() == Side.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new Runner());
        }
    }
}
