package com.useleess.litematicaupgraded.malilib.gui.config.registry;

import java.util.HashMap;
import javax.annotation.Nullable;

import com.useleess.litematicaupgraded.malilib.config.group.ExpandableConfigGroup;
import com.useleess.litematicaupgraded.malilib.config.group.PopupConfigGroup;
import com.useleess.litematicaupgraded.malilib.config.option.BooleanAndDoubleConfig;
import com.useleess.litematicaupgraded.malilib.config.option.BooleanAndFileConfig;
import com.useleess.litematicaupgraded.malilib.config.option.BooleanAndIntConfig;
import com.useleess.litematicaupgraded.malilib.config.option.BooleanConfig;
import com.useleess.litematicaupgraded.malilib.config.option.ColorConfig;
import com.useleess.litematicaupgraded.malilib.config.option.ConfigInfo;
import com.useleess.litematicaupgraded.malilib.config.option.DirectoryConfig;
import com.useleess.litematicaupgraded.malilib.config.option.DoubleConfig;
import com.useleess.litematicaupgraded.malilib.config.option.DualColorConfig;
import com.useleess.litematicaupgraded.malilib.config.option.FileConfig;
import com.useleess.litematicaupgraded.malilib.config.option.GenericButtonConfig;
import com.useleess.litematicaupgraded.malilib.config.option.HotkeyConfig;
import com.useleess.litematicaupgraded.malilib.config.option.HotkeyedBooleanConfig;
import com.useleess.litematicaupgraded.malilib.config.option.IntegerConfig;
import com.useleess.litematicaupgraded.malilib.config.option.OptionConfig;
import com.useleess.litematicaupgraded.malilib.config.option.StringConfig;
import com.useleess.litematicaupgraded.malilib.config.option.Vec2dConfig;
import com.useleess.litematicaupgraded.malilib.config.option.Vec2iConfig;
import com.useleess.litematicaupgraded.malilib.config.option.list.BlackWhiteListConfig;
import com.useleess.litematicaupgraded.malilib.config.option.list.BlockListConfig;
import com.useleess.litematicaupgraded.malilib.config.option.list.EquipmentSlotListConfig;
import com.useleess.litematicaupgraded.malilib.config.option.list.IdentifierListConfig;
import com.useleess.litematicaupgraded.malilib.config.option.list.ItemListConfig;
import com.useleess.litematicaupgraded.malilib.config.option.list.StatusEffectListConfig;
import com.useleess.litematicaupgraded.malilib.config.option.list.StringListConfig;
import com.useleess.litematicaupgraded.malilib.gui.config.ConfigOptionWidgetFactory;
import com.useleess.litematicaupgraded.malilib.gui.config.ConfigSearchInfo;
import com.useleess.litematicaupgraded.malilib.gui.config.MissingConfigTypeFactory;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.BlackWhiteListConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.BooleanAndDoubleConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.BooleanAndFileConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.BooleanAndIntConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.BooleanConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.ColorConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.CustomHotkeyEntryWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.DirectoryConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.DoubleConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.DualColorConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.ExpandableConfigGroupWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.FileConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.GenericButtonConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.HotkeyConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.HotkeyedBooleanConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.IntegerConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.OptionConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.PopupConfigGroupWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.StringConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.Vec2dConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.Vec2iConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.list.BlockListConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.list.EquipmentSlotListConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.list.IdentifierListConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.list.ItemListConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.list.StatusEffectListConfigWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.list.StringListConfigWidget;
import com.useleess.litematicaupgraded.malilib.input.CustomHotkeyDefinition;

@SuppressWarnings("unchecked")
public class ConfigWidgetRegistry
{
    protected final HashMap<Class<? extends ConfigInfo>, ConfigOptionWidgetFactory<?>> configWidgetFactories = new HashMap<>();
    protected final HashMap<Class<? extends ConfigInfo>, ConfigSearchInfo<?>> configSearchInfoMap = new HashMap<>();
    protected final ConfigOptionWidgetFactory<?> missingTypeFactory = new MissingConfigTypeFactory();

    public ConfigWidgetRegistry()
    {
        this.registerDefaultWidgetFactories();
        this.registerDefaultSearchInfos();
    }

    /**
     * Registers a config screen widget factory for the given config type
     */
    public <C extends ConfigInfo> void registerConfigWidgetFactory(Class<C> type, ConfigOptionWidgetFactory<C> factory)
    {
        this.configWidgetFactories.put(type, factory);
    }

    /**
     * Registers a config search info provider.
     * These are only needed if your custom config type has boolean/toggle
     * option(s) or hotkey(s) and you want to have your custom config searchable/filterable
     * using the dropdown widget in the search bar, using the toggle and hotkey related options there.
     */
    public <C extends ConfigInfo> void registerConfigSearchInfo(Class<C> type, ConfigSearchInfo<C> info)
    {
        this.configSearchInfoMap.put(type, info);
    }

    @SuppressWarnings("unchecked")
    public <C extends ConfigInfo> ConfigOptionWidgetFactory<C> getWidgetFactory(C config)
    {
        return (ConfigOptionWidgetFactory<C>) this.configWidgetFactories.getOrDefault(config.getClass(), this.missingTypeFactory);
    }

    public ConfigOptionWidgetFactory<?> getMissingTypeFactory()
    {
        return this.missingTypeFactory;
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public <C extends ConfigInfo> ConfigSearchInfo<C> getSearchInfo(C config)
    {
        return (ConfigSearchInfo<C>) this.configSearchInfoMap.get(config.getClass());
    }

    protected void registerDefaultWidgetFactories()
    {
        this.registerConfigWidgetFactory(BlackWhiteListConfig.class,    BlackWhiteListConfigWidget::new);
        this.registerConfigWidgetFactory(OptionConfig.class, OptionConfigWidget::new);

        this.registerConfigWidgetFactory(BlockListConfig.class,         BlockListConfigWidget::new);
        this.registerConfigWidgetFactory(BooleanConfig.class,           BooleanConfigWidget::new);
        this.registerConfigWidgetFactory(BooleanAndDoubleConfig.class,  BooleanAndDoubleConfigWidget::new);
        this.registerConfigWidgetFactory(BooleanAndFileConfig.class,    BooleanAndFileConfigWidget::new);
        this.registerConfigWidgetFactory(BooleanAndIntConfig.class,     BooleanAndIntConfigWidget::new);
        this.registerConfigWidgetFactory(ColorConfig.class,             ColorConfigWidget::new);
        this.registerConfigWidgetFactory(CustomHotkeyDefinition.class,  CustomHotkeyEntryWidget::new);
        this.registerConfigWidgetFactory(DirectoryConfig.class,         DirectoryConfigWidget::new);
        this.registerConfigWidgetFactory(DoubleConfig.class,            DoubleConfigWidget::new);
        this.registerConfigWidgetFactory(DualColorConfig.class,         DualColorConfigWidget::new);
        this.registerConfigWidgetFactory(EquipmentSlotListConfig.class, EquipmentSlotListConfigWidget::new);
        this.registerConfigWidgetFactory(ExpandableConfigGroup.class,   ExpandableConfigGroupWidget::new);
        this.registerConfigWidgetFactory(FileConfig.class,              FileConfigWidget::new);
        this.registerConfigWidgetFactory(GenericButtonConfig.class,     GenericButtonConfigWidget::new);
        this.registerConfigWidgetFactory(HotkeyConfig.class,            HotkeyConfigWidget::new);
        this.registerConfigWidgetFactory(HotkeyedBooleanConfig.class,   HotkeyedBooleanConfigWidget::new);
        this.registerConfigWidgetFactory(IdentifierListConfig.class,    IdentifierListConfigWidget::new);
        this.registerConfigWidgetFactory(IntegerConfig.class,           IntegerConfigWidget::new);
        this.registerConfigWidgetFactory(ItemListConfig.class,          ItemListConfigWidget::new);
        this.registerConfigWidgetFactory(PopupConfigGroup.class,        PopupConfigGroupWidget::new);
        this.registerConfigWidgetFactory(StatusEffectListConfig.class,  StatusEffectListConfigWidget::new);
        this.registerConfigWidgetFactory(StringConfig.class,            StringConfigWidget::new);
        this.registerConfigWidgetFactory(StringListConfig.class,        StringListConfigWidget::new);
        this.registerConfigWidgetFactory(Vec2dConfig.class,             Vec2dConfigWidget::new);
        this.registerConfigWidgetFactory(Vec2iConfig.class,             Vec2iConfigWidget::new);
    }

    protected void registerDefaultSearchInfos()
    {
        this.registerConfigSearchInfo(BooleanConfig.class,          new ConfigSearchInfo<BooleanConfig>(true, false).setBooleanStorageGetter((c) -> c));
        this.registerConfigSearchInfo(BooleanAndDoubleConfig.class, new ConfigSearchInfo<BooleanAndDoubleConfig>(true, false).setBooleanStorageGetter((c) -> c));
        this.registerConfigSearchInfo(BooleanAndFileConfig.class,   new ConfigSearchInfo<BooleanAndFileConfig>(true, false).setBooleanStorageGetter((c) -> c));
        this.registerConfigSearchInfo(BooleanAndIntConfig.class,    new ConfigSearchInfo<BooleanAndIntConfig>(true, false).setBooleanStorageGetter((c) -> c));
        this.registerConfigSearchInfo(HotkeyConfig.class,           new ConfigSearchInfo<HotkeyConfig>(false, true).setKeyBindGetter(HotkeyConfig::getKeyBind));
        this.registerConfigSearchInfo(HotkeyedBooleanConfig.class,  new ConfigSearchInfo<HotkeyedBooleanConfig>(true, true).setBooleanStorageGetter((c) -> c).setKeyBindGetter(HotkeyedBooleanConfig::getKeyBind));

        this.registerConfigSearchInfo(CustomHotkeyDefinition.class, new ConfigSearchInfo<CustomHotkeyDefinition>(false, true).setKeyBindGetter(CustomHotkeyDefinition::getKeyBind));
    }
}
