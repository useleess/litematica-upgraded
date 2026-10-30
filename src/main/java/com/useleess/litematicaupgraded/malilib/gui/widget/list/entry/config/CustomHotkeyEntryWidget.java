package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config;

import com.useleess.litematicaupgraded.malilib.gui.config.ConfigWidgetContext;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import com.useleess.litematicaupgraded.malilib.input.CustomHotkeyDefinition;

/**
 * This class is used when the custom hotkeys get pulled to the config search results.
 * This is not used on the Custom Hotkeys config screen,
 * see {@link com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.CustomHotkeyDefinitionEntryWidget} for that.
  */
public class CustomHotkeyEntryWidget extends BaseKeyBindConfigWidget
{
    public CustomHotkeyEntryWidget(CustomHotkeyDefinition config,
                                   DataListEntryWidgetData constructData,
                                   ConfigWidgetContext ctx)
    {
        super(config, constructData, ctx, config.getKeyBind());

        this.keybindButton.setEnabled(config.isLocked() == false);

        if (config.isLocked())
        {
            this.keybindButton.setHoverInfoRequiresShift(false);
        }
    }
}
