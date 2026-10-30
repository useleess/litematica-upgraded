package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config;

import com.useleess.litematicaupgraded.malilib.config.option.BooleanAndDoubleConfig;
import com.useleess.litematicaupgraded.malilib.config.option.BooleanAndDoubleConfig.BooleanAndDouble;
import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.malilib.gui.config.ConfigWidgetContext;
import com.useleess.litematicaupgraded.malilib.gui.widget.DoubleTextFieldWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;

public class BooleanAndDoubleConfigWidget extends BaseBooleanAndNumberConfigWidget<BooleanAndDouble, BooleanAndDoubleConfig>
{
    public BooleanAndDoubleConfigWidget(BooleanAndDoubleConfig config,
                                        DataListEntryWidgetData constructData,
                                        ConfigWidgetContext ctx)
    {
        super(config, constructData, ctx,
              BooleanAndDoubleConfig::setValueFromString, BooleanAndDoubleConfig::getStringValue);

        this.textField.setTextValidator(new DoubleTextFieldWidget.DoubleValidator(config.getMinDoubleValue(),
                                                                                  config.getMaxDoubleValue()));
        this.textField.translateAndAddHoverString("malilib.hover.config.numeric.range_and_default",
                                                  config.getMinDoubleValue(),
                                                  config.getMaxDoubleValue(),
                                                  config.getDefaultValue().doubleValue);
        this.sliderWidget.translateAndAddHoverString("malilib.hover.config.numeric.range_and_default",
                                                     config.getMinDoubleValue(),
                                                     config.getMaxDoubleValue(),
                                                     config.getDefaultValue().doubleValue);
    }

    @Override
    protected boolean onValueAdjustButtonClick(int mouseButton)
    {
        double amount = mouseButton == 1 ? -0.1 : 0.1;
        if (BaseScreen.isShiftDown()) { amount *= 20.0; }
        if (BaseScreen.isAltDown()) { amount *= 40.0; }

        this.config.setDoubleValue(this.config.getDoubleValue() + amount);
        this.updateWidgetState();

        return true;
    }
}
