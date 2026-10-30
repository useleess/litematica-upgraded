package com.useleess.litematicaupgraded.malilib.overlay.widget.sub;

import com.useleess.litematicaupgraded.malilib.MaLiLibReference;
import com.useleess.litematicaupgraded.malilib.config.option.DoubleConfig;
import com.useleess.litematicaupgraded.malilib.render.text.StyledTextLine;
import com.useleess.litematicaupgraded.malilib.util.data.ConfigOnTab;

public class DoubleConfigStatusWidget extends BaseConfigStatusIndicatorWidget<DoubleConfig>
{
    protected double lastValue;

    public DoubleConfigStatusWidget(DoubleConfig config, ConfigOnTab configOnTab)
    {
        this(config, configOnTab, MaLiLibReference.MOD_ID + ":csi_value_double");
    }

    public DoubleConfigStatusWidget(DoubleConfig config, ConfigOnTab configOnTab, String widgetTypeId)
    {
        super(config, configOnTab, widgetTypeId);
    }

    @Override
    public void updateState(boolean force)
    {
        if (force || this.lastValue != this.config.getDoubleValue())
        {
            this.updateValue();
        }
    }

    protected void updateValue()
    {
        this.lastValue = this.config.getDoubleValue();
        this.valueDisplayText = StyledTextLine.parseFirstLine(String.valueOf(this.lastValue));
        this.valueRenderWidth = this.valueDisplayText.renderWidth;
    }
}
