package com.useleess.litematicaupgraded.mixin;

import java.nio.file.Path;
import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.useleess.litematicaupgraded.convert.gui.MissingBlocksPrompt;
import com.useleess.litematicaupgraded.litematica.config.Configs;
import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.data.SchematicHolder;
import com.useleess.litematicaupgraded.litematica.gui.SchematicBrowserScreen;
import com.useleess.litematicaupgraded.litematica.schematic.LoadedSchematic;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacementManager;
import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntry;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntryType;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageDispatcher;

/** Loading a file with blocks 1.12 does not have opens the missing-blocks window first; the load goes on from its buttons. */
@Mixin(value = SchematicBrowserScreen.class, remap = false)
public abstract class MixinSchematicBrowserScreenModern extends BaseScreen
{
    @Inject(method = "loadSchematic", at = @At("HEAD"), cancellable = true)
    private void litematicaUpgradedAsk(CallbackInfo ci)
    {
        SchematicBrowserScreen self = (SchematicBrowserScreen) (Object) this;
        DirectoryEntry entry = self.getListWidget().getEntrySelectionHandler().getLastSelectedEntry();

        if (entry != null && entry.getType() == DirectoryEntryType.FILE &&
            MissingBlocksPrompt.open(entry.getFullPath(), this::litematicaUpgradedLoad))
        {
            ci.cancel();
        }
    }

    /** What the browser's load button does, for a file chosen by the window (it may have a new name). */
    private void litematicaUpgradedLoad(Path file)
    {
        SchematicBrowserScreen self = (SchematicBrowserScreen) (Object) this;
        Optional<LoadedSchematic> opt = LoadedSchematic.tryLoadSchematic(file);

        if (opt.isPresent() == false)
        {
            MessageDispatcher.error("litematica.message.error.schematic_load.failed_to_load", file.getFileName().toString());
            return;
        }

        LoadedSchematic loadedSchematic = opt.get();
        SchematicHolder.INSTANCE.addSchematic(loadedSchematic, true);
        MessageDispatcher.success("litematica.message.info.schematic_loaded_to_memory", file.getFileName().toString());
        self.setParent(null);

        if (Configs.Internal.CREATE_PLACEMENT_ON_LOAD.getBooleanValue())
        {
            SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
            manager.createPlacementForNewlyLoadedSchematic(loadedSchematic, isShiftDown() == false);
        }

        self.getListWidget().refreshEntries();
    }
}
