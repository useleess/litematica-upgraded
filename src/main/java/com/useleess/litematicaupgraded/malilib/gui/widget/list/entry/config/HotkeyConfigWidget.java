package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config;

import com.useleess.litematicaupgraded.malilib.config.option.HotkeyConfig;
import com.useleess.litematicaupgraded.malilib.gui.config.ConfigWidgetContext;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;

public class HotkeyConfigWidget extends BaseKeyBindConfigWidget
{
    public HotkeyConfigWidget(HotkeyConfig config,
                              DataListEntryWidgetData constructData,
                              ConfigWidgetContext ctx)
    {
        super(config, constructData, ctx, config.getKeyBind());

        this.keybindButton.setHoverStringProvider("locked", config::getLockAndOverrideMessages);
        this.keybindButton.setEnabled(config.isLocked() == false);

        if (config.isLocked())
        {
            this.keybindButton.setHoverInfoRequiresShift(false);
        }
    }
}
