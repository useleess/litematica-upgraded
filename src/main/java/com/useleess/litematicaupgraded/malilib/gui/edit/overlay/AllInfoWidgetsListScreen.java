package com.useleess.litematicaupgraded.malilib.gui.edit.overlay;

import com.useleess.litematicaupgraded.malilib.MaLiLibReference;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.BaseInfoRendererWidgetEntryWidget;
import com.useleess.litematicaupgraded.malilib.overlay.widget.InfoRendererWidget;

public class AllInfoWidgetsListScreen extends InfoRendererWidgetListScreen<InfoRendererWidget>
{
    public AllInfoWidgetsListScreen()
    {
        super(InfoRendererWidgetListScreen.createSupplierFromInfoManagerForSubtypes(InfoRendererWidget.class),
              null,
              BaseInfoRendererWidgetEntryWidget::new);

        this.setTitle("malilib.title.screen.configs.info_renderer_widgets", MaLiLibReference.MOD_VERSION);
    }
}
