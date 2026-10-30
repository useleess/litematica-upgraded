package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.list;

import com.useleess.litematicaupgraded.malilib.config.option.list.StringListConfig;
import com.useleess.litematicaupgraded.malilib.gui.config.ConfigWidgetContext;
import com.useleess.litematicaupgraded.malilib.gui.widget.button.BaseValueListEditButton;
import com.useleess.litematicaupgraded.malilib.gui.widget.button.GenericButton;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.StringListEditEntryWidget;
import com.useleess.litematicaupgraded.malilib.util.StringUtils;

public class StringListConfigWidget extends BaseValueListConfigWidget<String, StringListConfig>
{
    public StringListConfigWidget(StringListConfig config,
                                  DataListEntryWidgetData constructData,
                                  ConfigWidgetContext ctx)
    {
        super(config, constructData, ctx);
    }

    @Override
    protected GenericButton createButton(int width, int height, StringListConfig config, ConfigWidgetContext ctx)
    {
        String title = StringUtils.translate("malilib.title.screen.string_list_edit", this.config.getDisplayName());

        return new BaseValueListEditButton<>(width, height, config, this::updateWidgetState,
                                             () -> "", StringListEditEntryWidget::new, title);
    }
}
