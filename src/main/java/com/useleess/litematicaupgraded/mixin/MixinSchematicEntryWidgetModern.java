package com.useleess.litematicaupgraded.mixin;

import java.nio.file.Path;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.useleess.litematicaupgraded.convert.gui.MissingBlocksPrompt;
import com.useleess.litematicaupgraded.litematica.data.SchematicHolder;
import com.useleess.litematicaupgraded.litematica.gui.widget.list.entry.BaseSchematicEntryWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import com.useleess.litematicaupgraded.litematica.gui.widget.list.entry.SchematicEntryWidget;
import com.useleess.litematicaupgraded.litematica.schematic.LoadedSchematic;

/** Reloading a file with blocks 1.12 does not have opens the missing-blocks window first; the reload goes on from its buttons. */
@Mixin(value = SchematicEntryWidget.class, remap = false)
public abstract class MixinSchematicEntryWidgetModern extends BaseSchematicEntryWidget
{
    // listWidget is declared in a parent class: a @Shadow of it fails to apply (crashed the Loaded Schematics screen),
    // so this mixin extends the parent and uses the inherited field
    private MixinSchematicEntryWidgetModern(LoadedSchematic schematic, DataListEntryWidgetData constructData)
    {
        super(schematic, constructData);
    }

    @Shadow protected abstract void reloadFromFile();

    @Inject(method = "reloadFromFile", at = @At("HEAD"), cancellable = true)
    private void litematicaUpgradedAsk(CallbackInfo ci)
    {
        SchematicEntryWidget self = (SchematicEntryWidget) (Object) this;
        LoadedSchematic loaded = self.getData();

        if (loaded.file.isPresent() && MissingBlocksPrompt.open(loaded.file.get(), file -> this.litematicaUpgradedReload(loaded, file)))
        {
            ci.cancel();
        }
    }

    private void litematicaUpgradedReload(LoadedSchematic loaded, Path file)
    {
        if (loaded.file.isPresent() && loaded.file.get().equals(file))
        {
            // the same file (converted in place, or kept as it is): the normal reload without asking again
            MissingBlocksPrompt.skipOnce(file);
            this.reloadFromFile();
            return;
        }

        // a .schem or .nbt became a .litematic: the loaded schematic is replaced by the converted file
        SchematicHolder.INSTANCE.removeSchematic(loaded);
        SchematicHolder.INSTANCE.getOrLoad(file);
        this.listWidget.refreshEntries();
    }
}
