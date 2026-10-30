package com.useleess.litematicaupgraded.malilib.gui.edit;

import java.util.List;
import javax.annotation.Nullable;

import com.useleess.litematicaupgraded.malilib.gui.BaseTabbedScreen;
import com.useleess.litematicaupgraded.malilib.gui.tab.ScreenTab;
import com.useleess.litematicaupgraded.malilib.gui.widget.BaseLayerRangeEditWidget;
import com.useleess.litematicaupgraded.malilib.util.position.LayerRange;

public abstract class BaseLayerRangeEditScreen extends BaseTabbedScreen
{
    protected final BaseLayerRangeEditWidget editWidget;

    public BaseLayerRangeEditScreen(String screenId,
                                    List<? extends ScreenTab> screenTabs,
                                    @Nullable ScreenTab defaultTab,
                                    LayerRange range)
    {
        super(screenId, screenTabs, defaultTab);

        this.editWidget = new BaseLayerRangeEditWidget(-1, -1, range);
    }

    @Override
    protected void reAddActiveWidgets()
    {
        super.reAddActiveWidgets();

        this.addWidget(this.editWidget);
    }
}
