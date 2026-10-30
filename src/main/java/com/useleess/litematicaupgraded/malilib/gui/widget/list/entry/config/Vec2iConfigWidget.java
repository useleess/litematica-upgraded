package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config;

import com.useleess.litematicaupgraded.malilib.config.option.Vec2iConfig;
import com.useleess.litematicaupgraded.malilib.gui.config.ConfigWidgetContext;
import com.useleess.litematicaupgraded.malilib.gui.widget.BaseDualNumberEditWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.IntegerEditWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.Vec2iEditWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import com.useleess.litematicaupgraded.malilib.util.position.Vec2i;

public class Vec2iConfigWidget extends BaseDualNumberConfigWidget<Vec2i, Vec2iConfig, IntegerEditWidget>
{
    public Vec2iConfigWidget(Vec2iConfig config,
                             DataListEntryWidgetData constructData,
                             ConfigWidgetContext ctx)
    {
        super(config, constructData, ctx);
    }

    @Override
    protected BaseDualNumberEditWidget<Vec2i, IntegerEditWidget> createEditWidget(Vec2iConfig config)
    {
        Vec2iEditWidget widget = new Vec2iEditWidget(120, 18, 6, false, config.getValue(), this.config::setValue);
        int minValue = config.getMinValue();
        int maxValue = config.getMaxValue();
        widget.setValidRange(minValue, minValue, maxValue, maxValue);
        return widget;
    }
}
