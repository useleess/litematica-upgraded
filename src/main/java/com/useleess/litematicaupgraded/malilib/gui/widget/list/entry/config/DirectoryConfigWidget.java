package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config;

import java.nio.file.Path;

import com.useleess.litematicaupgraded.malilib.config.option.DirectoryConfig;
import com.useleess.litematicaupgraded.malilib.gui.config.ConfigWidgetContext;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;

public class DirectoryConfigWidget extends BaseFileConfigWidget<Path, DirectoryConfig>
{
    public DirectoryConfigWidget(DirectoryConfig config,
                                 DataListEntryWidgetData constructData,
                                 ConfigWidgetContext ctx)
    {
        super(config, constructData, ctx);
    }

    @Override
    protected Path getFileFromConfig()
    {
        return this.config.getValue();
    }

    @Override
    protected void setFileToConfig(Path file)
    {
        this.config.setValue(file);
    }
}
