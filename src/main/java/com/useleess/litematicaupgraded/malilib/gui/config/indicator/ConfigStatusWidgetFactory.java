package com.useleess.litematicaupgraded.malilib.gui.config.indicator;

import com.useleess.litematicaupgraded.malilib.config.option.ConfigInfo;
import com.useleess.litematicaupgraded.malilib.overlay.widget.sub.BaseConfigStatusIndicatorWidget;
import com.useleess.litematicaupgraded.malilib.util.data.ConfigOnTab;

public interface ConfigStatusWidgetFactory<C extends ConfigInfo>
{
    BaseConfigStatusIndicatorWidget<? extends ConfigInfo> create(C config, ConfigOnTab configOnTab);
}
