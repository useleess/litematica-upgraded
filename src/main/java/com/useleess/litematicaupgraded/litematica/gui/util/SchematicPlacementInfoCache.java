package com.useleess.litematicaupgraded.litematica.gui.util;

import java.nio.file.Path;
import java.util.HashMap;
import javax.annotation.Nullable;

import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;

public class SchematicPlacementInfoCache
{
    protected final HashMap<Path, SchematicPlacement> cachedData = new HashMap<>();

    @Nullable
    public SchematicPlacement getPlacementInfo(Path file)
    {
        return this.cachedData.get(file);
    }

    @Nullable
    public SchematicPlacement cacheAndGetPlacementInfo(Path file)
    {
        if (this.cachedData.containsKey(file) == false)
        {
            this.cachedData.put(file, SchematicPlacement.createFromFile(file));
        }

        return this.cachedData.get(file);
    }

    public void clearCache()
    {
        this.cachedData.clear();
    }
}
