package com.useleess.litematicaupgraded.litematica.gui.widget.list.entry;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;

import com.useleess.litematicaupgraded.malilib.gui.icon.FileBrowserIconProvider;
import com.useleess.litematicaupgraded.malilib.gui.widget.button.GenericButton;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.BaseFileBrowserWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntry;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.BaseFileBrowserWidget.DirectoryEntryType;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DirectoryEntryWidget;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageDispatcher;
import com.useleess.litematicaupgraded.malilib.util.FileNameUtils;
import com.useleess.litematicaupgraded.malilib.util.FileUtils;
import com.useleess.litematicaupgraded.litematica.data.DataManager;

public class SchematicPlacementBrowserEntryWidget extends DirectoryEntryWidget
{
    protected final GenericButton loadButton;
    protected final GenericButton removeButton;
    protected final boolean isSelectionEntry;

    public SchematicPlacementBrowserEntryWidget(DirectoryEntry entry,
                                                DataListEntryWidgetData constructData,
                                                BaseFileBrowserWidget fileBrowserWidget,
                                                @Nullable FileBrowserIconProvider iconProvider)
    {
        super(entry, constructData, fileBrowserWidget, iconProvider);

        this.isSelectionEntry = entry.getType() == DirectoryEntryType.FILE && entry.getName().endsWith(".json");
        this.loadButton   = GenericButton.create(18, "litematica.button.misc.load", this::loadSavedPlacement);
        this.removeButton = GenericButton.create(18, "litematica.button.misc.remove", this::removeSavedPlacement);
    }

    @Override
    public void reAddSubWidgets()
    {
        super.reAddSubWidgets();

        if (this.isSelectionEntry)
        {
            this.addWidget(this.loadButton);
            this.addWidget(this.removeButton);
        }
    }

    @Override
    public void updateSubWidgetPositions()
    {
        super.updateSubWidgetPositions();

        if (this.isSelectionEntry)
        {
            this.loadButton.centerVerticallyInside(this);
            this.removeButton.centerVerticallyInside(this);
            this.removeButton.setRight(this.getRight() - 2);
            this.loadButton.setRight(this.removeButton.getX() - 1);
        }
    }

    protected void loadSavedPlacement()
    {
        Path file = this.data.getFullPath();
        DataManager.getSchematicPlacementManager().loadPlacementFromFile(file);
    }

    protected void removeSavedPlacement()
    {
        this.scheduleTask(() -> {
            Path file = this.data.getFullPath();
            FileUtils.deleteFiles(Collections.singletonList(file), MessageDispatcher::error);
            this.listWidget.clearSelection();
            this.listWidget.refreshEntries();
        });
    }

    public static boolean placementSearchFilter(DirectoryEntry entry, List<String> searchTerms)
    {
        String fileName = FileNameUtils.getFileNameWithoutExtension(entry.getName()).toLowerCase(Locale.ROOT);

        for (String searchTerm : searchTerms)
        {
            if (fileName.contains(searchTerm))
            {
                return true;
            }
        }

        return false;
    }
}
