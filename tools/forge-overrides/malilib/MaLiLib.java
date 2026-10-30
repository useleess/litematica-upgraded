package malilib;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// Forge has no entrypoints: LitematicaUpgraded calls initClient() on the client init event, malilib first
public class MaLiLib
{
    public static final Logger LOGGER = LogManager.getLogger(MaLiLibReference.MOD_ID);

    public void initClient()
    {
    }

    public static boolean canShowCoordinates()
    {
        return MaLiLibConfigs.Generic.HIDE_ALL_COORDINATES.getBooleanValue() == false;
    }

    public static void debugLog(String str, Object... args)
    {
        if (MaLiLibConfigs.Debug.DEBUG_MESSAGES.getBooleanValue())
        {
            LOGGER.info(str, args);
        }
    }
}
