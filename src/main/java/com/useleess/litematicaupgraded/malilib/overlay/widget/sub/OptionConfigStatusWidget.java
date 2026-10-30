package com.useleess.litematicaupgraded.malilib.overlay.widget.sub;

import com.useleess.litematicaupgraded.malilib.MaLiLibReference;
import com.useleess.litematicaupgraded.malilib.config.option.OptionConfig;
import com.useleess.litematicaupgraded.malilib.config.value.OptionConfigValue;
import com.useleess.litematicaupgraded.malilib.render.text.StyledTextLine;
import com.useleess.litematicaupgraded.malilib.util.data.ConfigOnTab;

public class OptionConfigStatusWidget extends BaseConfigStatusIndicatorWidget<OptionConfig<OptionConfigValue>>
{
    protected OptionConfigValue lastValue;

    public OptionConfigStatusWidget(OptionConfig<OptionConfigValue> config, ConfigOnTab configOnTab)
    {
        this(config, configOnTab, MaLiLibReference.MOD_ID + ":csi_value_option_list");
    }

    public OptionConfigStatusWidget(OptionConfig<OptionConfigValue> config,
                                    ConfigOnTab configOnTab, String widgetTypeId)
    {
        super(config, configOnTab, widgetTypeId);

        this.valueColor = 0xFFFFA000;
    }

    @Override
    public void updateState(boolean force)
    {
        if (force || this.lastValue != this.config.getValue())
        {
            this.updateValue();
        }
    }

    protected void updateValue()
    {
        this.lastValue = this.config.getValue();
        this.valueDisplayText = StyledTextLine.parseFirstLine(this.lastValue.getDisplayName());
        this.valueRenderWidth = this.valueDisplayText.renderWidth;
    }
}
