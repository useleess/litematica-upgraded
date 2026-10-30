package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config;

import java.util.ArrayList;
import java.util.List;

import com.useleess.litematicaupgraded.malilib.MaLiLibConfigs;
import com.useleess.litematicaupgraded.malilib.config.option.OptionConfig;
import com.useleess.litematicaupgraded.malilib.config.value.OptionConfigValue;
import com.useleess.litematicaupgraded.malilib.gui.config.ConfigWidgetContext;
import com.useleess.litematicaupgraded.malilib.gui.widget.DropDownListWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.button.OptionConfigButton;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import com.useleess.litematicaupgraded.malilib.render.text.StyledTextLine;

public class OptionConfigWidget extends BaseConfigWidget<OptionConfig<OptionConfigValue>>
{
    protected final OptionConfig<OptionConfigValue> config;
    protected final OptionConfigValue initialValue;
    protected final OptionConfigButton optionConfigButton;
    protected final DropDownListWidget<OptionConfigValue> dropDownWidget;

    public OptionConfigWidget(OptionConfig<OptionConfigValue> config,
                              DataListEntryWidgetData constructData,
                              ConfigWidgetContext ctx)
    {
        super(config, constructData, ctx);

        this.config = config;
        this.initialValue = this.config.getValue();

        this.optionConfigButton = new OptionConfigButton(80, 20, this.config);
        this.optionConfigButton.setHoverStringProvider("locked", this.config::getLockAndOverrideMessages);
        this.optionConfigButton.setChangeListener(this::updateWidgetState);

        ArrayList<OptionConfigValue> values = new ArrayList<>(config.getAllowedValues());
        this.dropDownWidget = new DropDownListWidget<>(16, 12, values, OptionConfigValue::getDisplayName);
        this.dropDownWidget.setSelectedEntry(config.getValue());
        this.dropDownWidget.getHoverInfoFactory()
                .setTextLineProvider("list_preview", this::getOptionListPreviewHoverString, 99);
        this.dropDownWidget.setHoverStringProvider("locked", this.config::getLockAndOverrideMessages);
        this.dropDownWidget.setSelectionListener((v) -> {
            if (v != null)
            {
                this.config.setValue(v);
            }

            this.dropDownWidget.updateHoverStrings();
            this.updateWidgetState();
        });
    }

    @Override
    public void reAddSubWidgets()
    {
        super.reAddSubWidgets();

        boolean useDropDown = MaLiLibConfigs.Generic.OPTION_LIST_CONFIG_USE_DROPDOWN.getBooleanValue();

        this.addWidget(useDropDown ? this.dropDownWidget : this.optionConfigButton);
        this.addWidget(this.resetButton);
    }

    @Override
    public void updateSubWidgetPositions()
    {
        super.updateSubWidgetPositions();

        int x = this.getElementsStartPosition();
        int y = this.getY();
        int elementWidth = this.getElementWidth();

        this.dropDownWidget.setWidth(elementWidth);
        this.optionConfigButton.setWidth(elementWidth);

        this.optionConfigButton.setPosition(x, y + 1);
        this.dropDownWidget.setPosition(x, y + 2);

        this.resetButton.setPosition(x + elementWidth + 4, y + 1);
    }

    @Override
    public void updateWidgetState()
    {
        super.updateWidgetState();

        this.dropDownWidget.setEnabled(this.config.isLocked() == false);
        this.dropDownWidget.updateHoverStrings();
        this.optionConfigButton.setEnabled(this.config.isLocked() == false);
        this.optionConfigButton.updateButtonState();
    }

    @Override
    protected void onResetButtonClicked()
    {
        super.onResetButtonClicked();
        this.dropDownWidget.setSelectedEntry(this.config.getValue());
    }

    @Override
    public boolean wasModified()
    {
        return this.config.getValue().equals(this.initialValue) == false;
    }

    protected List<StyledTextLine> getOptionListPreviewHoverString(List<StyledTextLine> previousLines)
    {
        return OptionConfigButton.getOptionListPreviewHoverString(this.config, previousLines);
    }
}
