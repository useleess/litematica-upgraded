package com.useleess.litematicaupgraded.proxy;

import java.io.File;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

import com.useleess.litematicaupgraded.convert.MissingBlocks;
import com.useleess.litematicaupgraded.importer.OldSettingsImport;
import com.useleess.litematicaupgraded.litematica.Litematica;
import com.useleess.litematicaupgraded.malilib.MaLiLib;
import com.useleess.litematicaupgraded.printer.PrinterInit;
import com.useleess.litematicaupgraded.printer.ServerFeaturesClient;

/** The client: Litematica, malilib, the printer and the client side of the server part. */
public class ClientProxy extends CommonProxy {
    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);
        // the keybinds and settings of the mods the player used before, copied once on the first start
        OldSettingsImport.readOldFiles(event.getModConfigurationDirectory().getAbsoluteFile().getParentFile().toPath());
    }

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // malilib first, as its handlers must be registered before Litematica's
        new MaLiLib().initClient();
        new Litematica().initClient();
        // before the printer's own tick handler: a new capabilities message takes effect before the printer runs
        MinecraftForge.EVENT_BUS.register(ServerFeaturesClient.INSTANCE);
        PrinterInit.init();
        MinecraftForge.EVENT_BUS.register(OldSettingsImport.INSTANCE);
        // the blocks the player picked for blocks of newer versions, for every file
        MissingBlocks.load(new File(Loader.instance().getConfigDir(), "litematica_upgraded_missing_blocks.json"));
    }
}
