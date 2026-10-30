package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import com.useleess.litematicaupgraded.malilib.config.option.ConfigInfo;
import com.useleess.litematicaupgraded.malilib.gui.config.ConfigWidgetContext;
import com.useleess.litematicaupgraded.malilib.gui.widget.KeybindSettingsWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.button.KeyBindConfigButton;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import com.useleess.litematicaupgraded.malilib.input.KeyBind;

public abstract class BaseKeyBindConfigWidget extends BaseConfigWidget<ConfigInfo>
{
    protected final KeyBind keyBind;
    protected final IntArrayList initialValue = new IntArrayList();
    protected final KeyBindConfigButton keybindButton;
    protected final KeybindSettingsWidget settingsWidget;

    public BaseKeyBindConfigWidget(ConfigInfo config,
                                   DataListEntryWidgetData constructData,
                                   ConfigWidgetContext ctx,
                                   KeyBind keyBind)
    {
        super(config, constructData, ctx);

        this.keyBind = keyBind;
        keyBind.getKeysToList(this.initialValue);

        this.keybindButton = new KeyBindConfigButton(120, 20, keyBind);
        this.keybindButton.setValueChangeListener(this::onKeybindModified);
        this.keybindButton.setHoverInfoRequiresShift(true);

        this.settingsWidget = new KeybindSettingsWidget(keyBind, config.getDisplayName());
    }

    @Override
    public void reAddSubWidgets()
    {
        super.reAddSubWidgets();

        this.addWidget(this.keybindButton);
        this.addWidget(this.settingsWidget);
        this.addWidget(this.resetButton);
    }

    @Override
    public void updateSubWidgetPositions()
    {
        super.updateSubWidgetPositions();

        int y = this.getY() + 1;

        this.keybindButton.setPosition(this.getElementsStartPosition(), y);
        this.keybindButton.setWidth(this.getElementWidth() - 22);

        this.settingsWidget.setPosition(this.keybindButton.getRight() + 2, y);
        this.resetButton.setPosition(this.settingsWidget.getRight() + 4, y);
    }

    @Override
    public void updateWidgetState()
    {
        super.updateWidgetState();
        this.keybindButton.updateButtonState();
    }

    @Override
    public boolean wasModified()
    {
        return this.keyBind.matches(this.initialValue) == false;
    }

    @Override
    protected void onResetButtonClicked()
    {
        this.config.resetToDefault();
        this.ctx.getListWidget().refreshEntries();
    }

    protected void onKeybindModified()
    {
        this.ctx.getListWidget().refreshEntries();
    }
}
