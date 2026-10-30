package com.useleess.litematicaupgraded.litematica.gui.util;

import java.util.Locale;
import javax.annotation.Nullable;

import net.minecraft.client.renderer.texture.DynamicTexture;

import com.useleess.litematicaupgraded.malilib.util.FileNameUtils;
import com.useleess.litematicaupgraded.malilib.util.data.Identifier;
import com.useleess.litematicaupgraded.litematica.Reference;
import com.useleess.litematicaupgraded.litematica.schematic.LoadedSchematic;
import com.useleess.litematicaupgraded.litematica.schematic.Schematic;
import com.useleess.litematicaupgraded.litematica.schematic.SchematicMetadata;

public class SchematicInfoCacheBySchematic extends AbstractSchematicInfoCache<LoadedSchematic>
{
    @Override
    @Nullable
    protected SchematicInfo createSchematicInfo(LoadedSchematic loadedSchematic)
    {
        Schematic schematic = loadedSchematic.schematic;
        SchematicMetadata metadata = schematic.getMetadata();
        String name;

        if (loadedSchematic.file.isPresent())
        {
            name = FileNameUtils.generateSimpleSafeFileName(loadedSchematic.file.get().toAbsolutePath().toString().toLowerCase(Locale.ROOT));
        }
        else
        {
            name = FileNameUtils.generateSimpleSafeFileName(metadata.getSchematicName() + "_" + metadata.getAuthor() + "_" + metadata.getTimeCreated());
        }

        Identifier iconName = new Identifier(Reference.MOD_ID, name);
        DynamicTexture texture = this.createPreviewImage(iconName, metadata);
        return new SchematicInfo(schematic.getType(), metadata, iconName, texture);
    }
}
