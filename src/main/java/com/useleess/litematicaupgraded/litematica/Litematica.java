package com.useleess.litematicaupgraded.litematica;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.useleess.litematicaupgraded.malilib.registry.Registry;
import com.useleess.litematicaupgraded.litematica.config.Configs;

// Forge has no entrypoints: LitematicaUpgraded calls initClient() on the client init event
public class Litematica
{
    public static final Logger LOGGER = LogManager.getLogger(Reference.MOD_ID);

    public static void printDebug(String key, Object... args)
    {
        if (Configs.Generic.DEBUG_MESSAGES.getBooleanValue())
        {
            LOGGER.info(String.format(key, args));
        }
    }

    public void initClient()
    {
        Registry.INITIALIZATION_DISPATCHER.registerInitializationHandler(new InitHandler());
    }
}
