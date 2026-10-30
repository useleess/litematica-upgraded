package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry;

import com.useleess.litematicaupgraded.malilib.config.option.ConfigInfo;
import com.useleess.litematicaupgraded.malilib.render.text.StyledTextLine;
import com.useleess.litematicaupgraded.malilib.util.data.ConfigOnTab;

public class ConfigInfoEntryWidget extends BaseDataListEntryWidget<ConfigOnTab>
{
    public ConfigInfoEntryWidget(ConfigOnTab data, DataListEntryWidgetData constructData)
    {
        super(data, constructData);

        ConfigInfo config = data.getConfig();
        this.translateAndAddHoverString("malilib.hover.config.info_entry_widget",
                                        config.getDisplayName(), config.getName(),
                                        config.getClass().getSimpleName());

        this.setText(StyledTextLine.parseFirstLine(config.getDisplayName()));

        int bgColor = this.isOdd ? 0x30FFFFFF : 0x20FFFFFF;
        this.getBackgroundRenderer().getNormalSettings().setEnabledAndColor(true, bgColor);
    }
}
