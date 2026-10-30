package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config;

import com.useleess.litematicaupgraded.malilib.config.option.GenericButtonConfig;
import com.useleess.litematicaupgraded.malilib.gui.config.ConfigWidgetContext;
import com.useleess.litematicaupgraded.malilib.gui.widget.button.GenericButton;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;

public class GenericButtonConfigWidget extends BaseConfigWidget<GenericButtonConfig>
{
    protected final GenericButton button;

    public GenericButtonConfigWidget(GenericButtonConfig config,
                                     DataListEntryWidgetData constructData,
                                     ConfigWidgetContext ctx)
    {
        super(config, constructData, ctx);

        this.button = GenericButton.create(config.getButtonTextTranslationKey());
        this.button.setActionListener(config.getButtonActionListener());
    }

    @Override
    public void reAddSubWidgets()
    {
        super.reAddSubWidgets();
        this.addWidget(this.button);
    }

    @Override
    public void updateSubWidgetPositions()
    {
        super.updateSubWidgetPositions();
        this.button.setPosition(this.getElementsStartPosition(), this.getY() + 1);
    }
}
