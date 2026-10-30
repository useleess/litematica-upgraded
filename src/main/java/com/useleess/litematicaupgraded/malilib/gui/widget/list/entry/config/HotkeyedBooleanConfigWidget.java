package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config;

import com.useleess.litematicaupgraded.malilib.config.option.HotkeyedBooleanConfig;
import com.useleess.litematicaupgraded.malilib.gui.config.ConfigWidgetContext;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;

public class HotkeyedBooleanConfigWidget extends BaseHotkeyedBooleanConfigWidget<HotkeyedBooleanConfig>
{
    public HotkeyedBooleanConfigWidget(HotkeyedBooleanConfig config,
                                       DataListEntryWidgetData constructData,
                                       ConfigWidgetContext ctx)
    {
        super(config, config, config.getKeyBind(), constructData, ctx);
    }
}
