package com.useleess.litematicaupgraded.malilib.gui.config;

import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;

import com.useleess.litematicaupgraded.malilib.MaLiLibConfigs;
import com.useleess.litematicaupgraded.malilib.config.ConfigManagerImpl;
import com.useleess.litematicaupgraded.malilib.gui.BaseListScreen;
import com.useleess.litematicaupgraded.malilib.gui.tab.ScreenTab;
import com.useleess.litematicaupgraded.malilib.gui.widget.button.GenericButton;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.ConfigOptionListWidget;
import com.useleess.litematicaupgraded.malilib.listener.EventListener;
import com.useleess.litematicaupgraded.malilib.registry.Registry;
import com.useleess.litematicaupgraded.malilib.util.ListUtils;
import com.useleess.litematicaupgraded.malilib.util.data.ConfigOnTab;
import com.useleess.litematicaupgraded.malilib.util.data.ModInfo;

public class BaseConfigScreen extends BaseListScreen<ConfigOptionListWidget>
{
    protected final ModInfo modInfo;
    @Nullable protected EventListener configSaveListener;
    protected int configElementsWidth = 120;

    public BaseConfigScreen(ModInfo modInfo,
                            List<? extends ScreenTab> configTabs,
                            @Nullable ConfigTab defaultTab,
                            String titleKey, Object... args)
    {
        super(10, 46, 20, 54, modInfo.getModId(), configTabs, defaultTab);

        this.modInfo = modInfo;
        this.shouldRestoreScrollbarPosition = MaLiLibConfigs.Generic.REMEMBER_CONFIG_TAB_SCROLL_POSITIONS.getBooleanValue();

        this.addPreScreenCloseListener(this::saveConfigsOnScreenClose);
        this.createSwitchModConfigScreenDropDown(modInfo);
        this.setTitle(titleKey, args);
    }

    protected void saveConfigsOnScreenClose()
    {
        if (((ConfigManagerImpl) Registry.CONFIG_MANAGER).saveIfDirty())
        {
            this.onSettingsChanged();
        }
    }

    public void setConfigSaveListener(@Nullable EventListener configSaveListener)
    {
        this.configSaveListener = configSaveListener;
    }

    public int getDefaultConfigElementWidth()
    {
        ScreenTab tab = this.getCurrentTab();

        if (tab instanceof ConfigTab)
        {
            return ((ConfigTab) tab).getConfigWidgetsWidth();
        }

        return this.configElementsWidth;
    }

    /**
     * Sets the requested config elements width for this screen.
     * Use -1 to indicate automatic/default width decided by the widgets.
     */
    public BaseConfigScreen setConfigElementsWidth(int configElementsWidth)
    {
        this.configElementsWidth = configElementsWidth;
        return this;
    }

    public ModInfo getModInfo()
    {
        return this.modInfo;
    }

    @Override
    public void switchToTab(ScreenTab tab)
    {
        this.saveScrollBarPositionForCurrentTab();

        super.switchToTab(tab);

        this.restoreScrollBarPositionForCurrentTab();
        this.reCreateConfigWidgets();
    }

    public void reCreateConfigWidgets()
    {
        for (GenericButton tabButton : this.tabButtons)
        {
            tabButton.updateButtonState();
        }

        ConfigOptionListWidget listWidget = this.getListWidget();

        if (listWidget != null)
        {
            listWidget.refreshEntries();
        }
    }

    protected void onSettingsChanged()
    {
        Registry.HOTKEY_MANAGER.updateUsedKeys();

        if (this.configSaveListener != null)
        {
            this.configSaveListener.onEvent();
        }
    }

    public List<ConfigOnTab> getConfigs()
    {
        ScreenTab tab = this.getCurrentTab();

        if (tab instanceof ConfigTab)
        {
            return ((ConfigTab) tab).getTabbedConfigs();
        }

        return Collections.emptyList();
    }

    @Override
    protected ConfigOptionListWidget createListWidget()
    {
        ConfigOptionListWidget listWidget = new ConfigOptionListWidget(this::getDefaultConfigElementWidth,
                                                                       this.modInfo, this::getConfigs);
        listWidget.addConfigSearchBarWidget();
        return listWidget;
    }

    public static BaseConfigScreen withExtensionModTabs(ModInfo modInfo,
                                                        List<? extends ScreenTab> configTabs,
                                                        @Nullable ConfigTab defaultTab,
                                                        String titleKey, Object... args)
    {
        List<? extends ScreenTab> extraTabs = Registry.CONFIG_TAB.getExtraConfigScreenTabsFor(modInfo);
        List<ScreenTab> allTabs = ListUtils.getAppendedList(configTabs, extraTabs);
        return new BaseConfigScreen(modInfo, allTabs, defaultTab, titleKey, args);
    }
}
