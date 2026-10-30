package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config;

import java.util.List;

import com.useleess.litematicaupgraded.malilib.config.group.PopupConfigGroup;
import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.malilib.gui.config.BaseConfigGroupEditScreen;
import com.useleess.litematicaupgraded.malilib.gui.config.ConfigWidgetContext;
import com.useleess.litematicaupgraded.malilib.gui.util.GuiUtils;
import com.useleess.litematicaupgraded.malilib.gui.widget.button.GenericButton;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import com.useleess.litematicaupgraded.malilib.render.text.StyledTextLine;

public class PopupConfigGroupWidget extends BaseConfigWidget<PopupConfigGroup>
{
    protected final PopupConfigGroup config;
    protected final GenericButton groupOpenButton;

    public PopupConfigGroupWidget(PopupConfigGroup config,
                                  DataListEntryWidgetData constructData,
                                  ConfigWidgetContext ctx)
    {
        super(config, constructData, ctx);

        this.config = config;
        this.groupOpenButton = GenericButton.create("malilib.button.config.popup_group.show_configs",
                                                    this::openConfigGroupEditScreen);
        this.groupOpenButton.getHoverInfoFactory().setTextLineProvider("config_list", this::getContainedConfigsHoverInfo);

        this.getHoverInfoFactory().setTextLineProvider("config_list", this::getContainedConfigsHoverInfo);
    }

    @Override
    public void reAddSubWidgets()
    {
        super.reAddSubWidgets();
        this.addWidget(this.groupOpenButton);
    }

    @Override
    public void updateSubWidgetPositions()
    {
        super.updateSubWidgetPositions();
        this.groupOpenButton.setPosition(this.getElementsStartPosition(), this.getY());
    }

    protected void openConfigGroupEditScreen()
    {
        BaseConfigGroupEditScreen screen = new BaseConfigGroupEditScreen(this.config.getModInfo(), null);
        screen.setScreenWidth(Math.max(520, GuiUtils.getScaledWindowWidth() - 80));
        screen.setConfigs(this.config.getConfigs());
        screen.setTitle(this.config.getDisplayName());
        BaseScreen.openPopupScreenWithCurrentScreenAsParent(screen);
    }

    protected List<StyledTextLine> getContainedConfigsHoverInfo()
    {
        return ExpandableConfigGroupWidget.getContainedConfigsHoverInfo(this.config.getConfigs());
    }
}
