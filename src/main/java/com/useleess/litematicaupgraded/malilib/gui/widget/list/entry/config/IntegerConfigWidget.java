package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config;

import com.useleess.litematicaupgraded.malilib.config.option.IntegerConfig;
import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.malilib.gui.config.ConfigWidgetContext;
import com.useleess.litematicaupgraded.malilib.gui.widget.IntegerTextFieldWidget.IntRangeValidator;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;

public class IntegerConfigWidget extends NumericConfigWidget<Integer, IntegerConfig>
{
    public IntegerConfigWidget(IntegerConfig config,
                               DataListEntryWidgetData constructData,
                               ConfigWidgetContext ctx)
    {
        super(config, constructData, ctx,
              IntegerConfig::setValueFromString, IntegerConfig::getStringValue);

        this.textField.setTextValidator(new IntRangeValidator(config.getMinIntegerValue(),
                                                              config.getMaxIntegerValue()));
        this.textField.translateAndAddHoverString("malilib.hover.config.numeric.range_and_default",
                                                  config.getMinIntegerValue(),
                                                  config.getMaxIntegerValue(),
                                                  config.getDefaultIntegerValue());
        this.sliderWidget.translateAndAddHoverString("malilib.hover.config.numeric.range_and_default",
                                                     config.getMinIntegerValue(),
                                                     config.getMaxIntegerValue(),
                                                     config.getDefaultIntegerValue());
    }

    @Override
    protected boolean onValueAdjustButtonClick(int mouseButton)
    {
        int amount = mouseButton == 1 ? -1 : 1;
        if (BaseScreen.isShiftDown()) { amount *= 8; }
        if (BaseScreen.isAltDown()) { amount *= 4; }

        this.config.setIntegerValue(this.config.getIntegerValue() + amount);
        this.updateWidgetState();

        return true;
    }
}
