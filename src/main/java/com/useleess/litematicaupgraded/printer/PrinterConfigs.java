package com.useleess.litematicaupgraded.printer;

import java.util.List;

import com.google.common.collect.ImmutableList;

import com.useleess.litematicaupgraded.LitematicaUpgraded;
import com.useleess.litematicaupgraded.malilib.config.category.BaseConfigOptionCategory;
import com.useleess.litematicaupgraded.malilib.config.category.ConfigOptionCategory;
import com.useleess.litematicaupgraded.malilib.config.option.BooleanConfig;
import com.useleess.litematicaupgraded.malilib.config.option.ConfigOption;
import com.useleess.litematicaupgraded.malilib.config.option.HotkeyedBooleanConfig;
import com.useleess.litematicaupgraded.malilib.config.option.IntegerConfig;
import com.useleess.litematicaupgraded.malilib.config.option.StringConfig;
import com.useleess.litematicaupgraded.malilib.input.Hotkey;
import com.useleess.litematicaupgraded.malilib.util.data.ModInfo;
import com.useleess.litematicaupgraded.perf.PerfConfigs;

/** The settings of Litematica Upgraded's own features, in the "Litematica Upgraded" tab of Litematica's config screen. */
public final class PrinterConfigs {
    public static final ModInfo MOD_INFO = new ModInfo(LitematicaUpgraded.MOD_ID, "Litematica Upgraded");
    public static final int CURRENT_VERSION = 1;

    public static final HotkeyedBooleanConfig PRINTER_ENABLED = new HotkeyedBooleanConfig("printerEnabled", false, "M,P");
    public static final IntegerConfig RANGE = new IntegerConfig("printerRange", 4, 1, 8);
    public static final IntegerConfig BLOCKS_PER_TICK = new IntegerConfig("printerBlocksPerTick", 2, 1, 16);
    public static final IntegerConfig COOLDOWN = new IntegerConfig("printerCooldownTicks", 4, 1, 40);
    public static final StringConfig SHAPE = new StringConfig("printerShape", "sphere");
    public static final StringConfig AXIS_ORDER = new StringConfig("printerAxisOrder", "xzy");
    public static final BooleanConfig FAKE_ROTATION = new BooleanConfig("printerFakeRotation", true);
    public static final BooleanConfig FAKE_POSITION = new BooleanConfig("printerFakePosition", true);
    public static final BooleanConfig REDSTONE_ORDER = new BooleanConfig("printerRedstoneOrder", true);
    public static final BooleanConfig PAUSE_WHILE_MINING = new BooleanConfig("printerPauseWhileMining", true);
    public static final BooleanConfig EASY_PLACE_FAKE_ROTATION = new BooleanConfig("easyPlaceFakeRotation", true);
    public static final BooleanConfig BREAK_WRONG_BLOCK = new BooleanConfig("printerBreakWrongBlock", false);
    public static final BooleanConfig BREAK_EXTRA_BLOCK = new BooleanConfig("printerBreakExtraBlock", false);
    public static final BooleanConfig BREAK_WRONG_STATE = new BooleanConfig("printerBreakWrongStateBlock", false);
    public static final BooleanConfig AUTO_TOOL = new BooleanConfig("printerAutoTool", true);
    public static final BooleanConfig USE_TOOLS = new BooleanConfig("printerUseTools", true);
    public static final BooleanConfig SCAFFOLD = new BooleanConfig("printerScaffold", true);
    public static final BooleanConfig FILL_CONTAINERS = new BooleanConfig("printerFillContainers", false);
    public static final BooleanConfig FLUIDS = new BooleanConfig("printerFluids", true);
    public static final BooleanConfig ICE_WATER = new BooleanConfig("printerIceWater", true);
    public static final BooleanConfig SNOW_LAYERS = new BooleanConfig("printerSnowLayers", true);
    public static final BooleanConfig SIGN_TEXT = new BooleanConfig("printerSignTextPaste", true);
    public static final BooleanConfig DESTROY_ASSIST = new BooleanConfig("destroyAssist", false);
    public static final BooleanConfig LAYER_LIMIT = new BooleanConfig("printerLimitToLayerRange", true);
    public static final BooleanConfig STOP_AFTER_DEATH = new BooleanConfig("printerStopAfterDeath", true);
    /** Not on the screen: set once the keybinds and settings of the mods players come from were copied (importer). */
    public static final BooleanConfig OLD_SETTINGS_IMPORTED = new BooleanConfig("oldSettingsImported", false);
    /** Only a tooltip: the credits for the code the printer comes from. */
    public static final StringConfig CREDITS = new StringConfig("credits", "");

    public static final ImmutableList<ConfigOption<?>> PRINTER_OPTIONS = ImmutableList.of(
            PRINTER_ENABLED,
            RANGE,
            BLOCKS_PER_TICK,
            COOLDOWN,
            SHAPE,
            AXIS_ORDER,
            FAKE_ROTATION,
            FAKE_POSITION,
            REDSTONE_ORDER,
            PAUSE_WHILE_MINING,
            EASY_PLACE_FAKE_ROTATION,
            BREAK_WRONG_BLOCK,
            BREAK_EXTRA_BLOCK,
            BREAK_WRONG_STATE,
            AUTO_TOOL,
            USE_TOOLS,
            SCAFFOLD,
            FILL_CONTAINERS,
            FLUIDS,
            ICE_WATER,
            SNOW_LAYERS,
            SIGN_TEXT,
            DESTROY_ASSIST,
            LAYER_LIMIT,
            STOP_AFTER_DEATH,
            CREDITS
    );

    /** Everything in the tab: the printer's settings, then the speed settings. */
    public static final ImmutableList<ConfigOption<?>> OPTIONS = ImmutableList.<ConfigOption<?>>builder().addAll(PRINTER_OPTIONS).addAll(PerfConfigs.OPTIONS).build();

    public static final ImmutableList<Hotkey> HOTKEYS = ImmutableList.of(PRINTER_ENABLED);

    public static final List<ConfigOptionCategory> CATEGORIES = ImmutableList.of(
            BaseConfigOptionCategory.normal(MOD_INFO, "Printer", PRINTER_OPTIONS),
            BaseConfigOptionCategory.normal(MOD_INFO, "Performance", PerfConfigs.OPTIONS),
            BaseConfigOptionCategory.normal(MOD_INFO, "Internal", ImmutableList.of(OLD_SETTINGS_IMPORTED))
    );

    private PrinterConfigs() {}
}
