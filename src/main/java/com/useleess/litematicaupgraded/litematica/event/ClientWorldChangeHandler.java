package com.useleess.litematicaupgraded.litematica.event;

import javax.annotation.Nullable;

import net.minecraft.client.multiplayer.WorldClient;

import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.world.SchematicWorldHandler;

public class ClientWorldChangeHandler implements com.useleess.litematicaupgraded.malilib.event.ClientWorldChangeHandler
{
    @Override
    public void onPreClientWorldChange(@Nullable WorldClient worldBefore, @Nullable WorldClient worldAfter)
    {
        // Save the settings before the integrated server gets shut down
        if (worldBefore != null)
        {
            boolean isDimensionChange = worldAfter != null;
            DataManager.save(isDimensionChange);
        }
    }

    @Override
    public void onPostClientWorldChange(@Nullable WorldClient worldBefore, @Nullable WorldClient worldAfter)
    {
        SchematicWorldHandler.recreateSchematicWorld(worldAfter == null);

        if (worldAfter != null)
        {
            boolean isDimensionChange = worldBefore != null;
            DataManager.load(isDimensionChange);
        }
        else
        {
            DataManager.clear();
        }
    }
}
