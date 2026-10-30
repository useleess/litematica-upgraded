package com.useleess.litematicaupgraded.malilib.gui.config;

import com.useleess.litematicaupgraded.malilib.config.option.ConfigInfo;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.BaseConfigWidget;

public interface ConfigOptionWidgetFactory<CFG extends ConfigInfo>
{
    BaseConfigWidget<? extends ConfigInfo> create(CFG config,
                                                  DataListEntryWidgetData constructData,
                                                  ConfigWidgetContext ctx);
}
