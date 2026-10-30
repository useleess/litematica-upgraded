package com.useleess.litematicaupgraded.litematica.gui.widget;

import com.useleess.litematicaupgraded.litematica.schematic.LoadedSchematic;
import com.useleess.litematicaupgraded.litematica.gui.util.SchematicInfoCacheBySchematic;

public class SchematicInfoWidgetBySchematic extends AbstractSchematicInfoWidget<LoadedSchematic>
{
    public SchematicInfoWidgetBySchematic(int width, int height)
    {
        super(width, height, new SchematicInfoCacheBySchematic());
    }
}
