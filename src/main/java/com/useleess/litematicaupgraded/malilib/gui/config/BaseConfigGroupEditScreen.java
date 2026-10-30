package com.useleess.litematicaupgraded.malilib.gui.config;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import com.useleess.litematicaupgraded.malilib.MaLiLibConfigScreen;
import com.useleess.litematicaupgraded.malilib.config.option.ConfigInfo;
import com.useleess.litematicaupgraded.malilib.gui.BaseListScreen;
import com.useleess.litematicaupgraded.malilib.gui.util.GuiUtils;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.ConfigOptionListWidget;
import com.useleess.litematicaupgraded.malilib.util.data.ConfigOnTab;
import com.useleess.litematicaupgraded.malilib.util.data.ModInfo;

public class BaseConfigGroupEditScreen extends BaseListScreen<ConfigOptionListWidget>
{
    protected final ArrayList<ConfigInfo> configs = new ArrayList<>();
    protected final ModInfo modInfo;
    protected int elementsWidth = 200;

    public BaseConfigGroupEditScreen(ModInfo modInfo, @Nullable Runnable saveListener)
    {
        super(8, 30, 14, 36);

        this.modInfo = modInfo;
        this.shouldCenter = true;
        this.renderBorder = true;
        this.useTitleHierarchy = false;
        this.backgroundColor = 0xFF000000;
        this.screenWidth = Math.min(350, GuiUtils.getScaledWindowWidth() - 40);
        this.screenHeight = GuiUtils.getScaledWindowHeight() - 90;

        if (saveListener != null)
        {
            this.addPreScreenCloseListener(saveListener);
        }
    }

    public void setConfigs(List<? extends ConfigInfo> configs)
    {
        this.configs.clear();
        this.configs.addAll(configs);
    }

    protected List<ConfigOnTab> getConfigs()
    {
        ArrayList<ConfigOnTab> list = new ArrayList<>();
        ConfigTab tab = new BaseConfigTab(ModInfo.NO_MOD, "fake", "Fake", 200,
                                          this.configs, scr -> scr instanceof BaseConfigScreen,
                                          MaLiLibConfigScreen::create);

        for (ConfigInfo cfg : this.configs)
        {
            list.add(new ConfigOnTab(tab, cfg));
        }

        return list;
    }

    protected int getElementsWidth()
    {
        return this.elementsWidth;
    }

    @Override
    protected ConfigOptionListWidget createListWidget()
    {
        return new ConfigOptionListWidget(this::getElementsWidth, this.modInfo, this::getConfigs);
    }
}
