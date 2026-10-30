package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config;

import com.useleess.litematicaupgraded.malilib.config.option.BooleanAndIntConfig;
import com.useleess.litematicaupgraded.malilib.config.option.BooleanAndIntConfig.BooleanAndInt;
import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.malilib.gui.config.ConfigWidgetContext;
import com.useleess.litematicaupgraded.malilib.gui.widget.IntegerTextFieldWidget.IntRangeValidator;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;

public class BooleanAndIntConfigWidget extends BaseBooleanAndNumberConfigWidget<BooleanAndInt, BooleanAndIntConfig>
{
    public BooleanAndIntConfigWidget(BooleanAndIntConfig config,
                                     DataListEntryWidgetData constructData,
                                     ConfigWidgetContext ctx)
    {
        super(config, constructData, ctx,
              BooleanAndIntConfig::setValueFromString, BooleanAndIntConfig::getStringValue);

        this.textField.setTextValidator(new IntRangeValidator(config.getMinIntegerValue(),
                                                              config.getMaxIntegerValue()));
        this.textField.translateAndAddHoverString("malilib.hover.config.numeric.range_and_default",
                                                  config.getMinIntegerValue(),
                                                  config.getMaxIntegerValue(),
                                                  config.getDefaultValue().intValue);
        this.sliderWidget.translateAndAddHoverString("malilib.hover.config.numeric.range_and_default",
                                                     config.getMinIntegerValue(),
                                                     config.getMaxIntegerValue(),
                                                     config.getDefaultValue().intValue);
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
