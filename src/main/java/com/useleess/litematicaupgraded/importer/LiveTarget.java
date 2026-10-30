package com.useleess.litematicaupgraded.importer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonPrimitive;
import it.unimi.dsi.fastutil.ints.IntArrayList;

import com.useleess.litematicaupgraded.importer.ImportCore.Outcome;
import com.useleess.litematicaupgraded.litematica.config.Configs;
import com.useleess.litematicaupgraded.litematica.config.Hotkeys;
import com.useleess.litematicaupgraded.malilib.MaLiLibConfigs;
import com.useleess.litematicaupgraded.malilib.config.option.BaseGenericConfig;
import com.useleess.litematicaupgraded.malilib.config.option.BooleanAndDoubleConfig;
import com.useleess.litematicaupgraded.malilib.config.option.BooleanAndFileConfig;
import com.useleess.litematicaupgraded.malilib.config.option.BooleanAndIntConfig;
import com.useleess.litematicaupgraded.malilib.config.option.BooleanConfig;
import com.useleess.litematicaupgraded.malilib.config.option.ColorConfig;
import com.useleess.litematicaupgraded.malilib.config.option.ConfigOption;
import com.useleess.litematicaupgraded.malilib.config.option.DoubleConfig;
import com.useleess.litematicaupgraded.malilib.config.option.IntegerConfig;
import com.useleess.litematicaupgraded.malilib.config.option.OptionConfig;
import com.useleess.litematicaupgraded.malilib.config.option.StringConfig;
import com.useleess.litematicaupgraded.malilib.config.option.Vec2iConfig;
import com.useleess.litematicaupgraded.malilib.config.value.OptionConfigValue;
import com.useleess.litematicaupgraded.malilib.input.Hotkey;
import com.useleess.litematicaupgraded.malilib.input.KeyBind;
import com.useleess.litematicaupgraded.malilib.input.Keys;
import com.useleess.litematicaupgraded.malilib.util.position.Vec2i;
import com.useleess.litematicaupgraded.printer.PrinterConfigs;

/**
 * The copy's view of our real options and hotkeys: Litematica's, malilib's ("malilib:" names) and the "Litematica Upgraded"
 * tab's. Whether the printer is on is a state, not a setting: never copied (its hotkey is).
 */
final class LiveTarget implements ImportCore.Target {
    private final Map<String, ConfigOption<?>> settings = new LinkedHashMap<>();
    private final Map<String, KeyBind> hotkeys = new LinkedHashMap<>();
    private final Path gameDir;

    LiveTarget(Path gameDir) {
        this.gameDir = gameDir;
        for (List<ConfigOption<?>> list : java.util.Arrays.asList(Configs.Generic.OPTIONS, Configs.Visuals.OPTIONS,
                Configs.InfoOverlays.OPTIONS, Configs.Colors.OPTIONS, Configs.Nags.OPTIONS, PrinterConfigs.OPTIONS)) {
            for (ConfigOption<?> option : list) if (option != PrinterConfigs.CREDITS && option != PrinterConfigs.PRINTER_ENABLED) settings.putIfAbsent(option.getName(), option);
        }
        for (ConfigOption<?> option : MaLiLibConfigs.Generic.OPTIONS) settings.put(SourceReader.MALILIB + option.getName(), option);
        List<Hotkey> ours = new ArrayList<>(Hotkeys.HOTKEY_LIST);
        ours.addAll(Configs.Generic.HOTKEYS);
        ours.addAll(Configs.Visuals.HOTKEYS);
        ours.addAll(Configs.InfoOverlays.HOTKEYS);
        ours.addAll(PrinterConfigs.HOTKEYS);
        for (Hotkey hotkey : ours) hotkeys.putIfAbsent(hotkey.getName(), hotkey.getKeyBind());
        for (Hotkey hotkey : MaLiLibConfigs.Hotkeys.FUNCTIONAL_HOTKEYS) hotkeys.put(SourceReader.MALILIB + hotkey.getName(), hotkey.getKeyBind());
    }

    @Override
    public boolean hasSetting(String name) {
        return settings.containsKey(name);
    }

    @Override
    public boolean settingModified(String name) {
        ConfigOption<?> option = settings.get(name);
        if (option instanceof BaseGenericConfig) {
            BaseGenericConfig<?> config = (BaseGenericConfig<?>) option;
            return !Objects.equals(config.getValue(), config.getDefaultValue());
        }
        return option.isModified();
    }

    @Override
    public Outcome setSetting(String name, String part, JsonPrimitive value) {
        ConfigOption<?> option = settings.get(name);
        if (option == null) return Outcome.UNKNOWN;
        if (option instanceof BooleanAndDoubleConfig) {
            BooleanAndDoubleConfig config = (BooleanAndDoubleConfig) option;
            BooleanAndDoubleConfig.BooleanAndDouble now = config.getValue();
            if (part.isEmpty()) return value.isBoolean() ? set(config, new BooleanAndDoubleConfig.BooleanAndDouble(value.getAsBoolean(), now.doubleValue)) : Outcome.BAD;
            if (!part.equals("d") || !fits(value, config.getMinDoubleValue(), config.getMaxDoubleValue(), false)) return Outcome.BAD;
            return set(config, new BooleanAndDoubleConfig.BooleanAndDouble(now.booleanValue, value.getAsDouble()));
        }
        if (option instanceof BooleanAndIntConfig) {
            BooleanAndIntConfig config = (BooleanAndIntConfig) option;
            BooleanAndIntConfig.BooleanAndInt now = config.getValue();
            if (part.isEmpty()) return value.isBoolean() ? set(config, new BooleanAndIntConfig.BooleanAndInt(value.getAsBoolean(), now.intValue)) : Outcome.BAD;
            if (!part.equals("i") || !fits(value, config.getMinIntegerValue(), config.getMaxIntegerValue(), true)) return Outcome.BAD;
            return set(config, new BooleanAndIntConfig.BooleanAndInt(now.booleanValue, value.getAsInt()));
        }
        if (option instanceof BooleanAndFileConfig) {
            BooleanAndFileConfig config = (BooleanAndFileConfig) option;
            if (!part.equals("dir") || !value.isString() || value.getAsString().trim().isEmpty()) return Outcome.BAD;
            Path dir = gameDir.resolve(Paths.get(value.getAsString().trim())).normalize();
            if (dir.equals(config.getValue().fileValue.toAbsolutePath().normalize())) return Outcome.SAME;
            return set(config, new BooleanAndFileConfig.BooleanAndFile(true, dir));
        }
        if (option instanceof Vec2iConfig) {
            Vec2iConfig config = (Vec2iConfig) option;
            Vec2i now = config.getValue();
            if (!(part.equals("x") || part.equals("y")) || !fits(value, config.getMinValue(), config.getMaxValue(), true)) return Outcome.BAD;
            return set(config, part.equals("x") ? new Vec2i(value.getAsInt(), now.y) : new Vec2i(now.x, value.getAsInt()));
        }
        if (!part.isEmpty()) return Outcome.BAD;
        if (option instanceof BooleanConfig) {
            return value.isBoolean() ? set((BooleanConfig) option, value.getAsBoolean()) : Outcome.BAD;
        }
        if (option instanceof IntegerConfig) {
            IntegerConfig config = (IntegerConfig) option;
            return fits(value, config.getMinIntegerValue(), config.getMaxIntegerValue(), true) ? set(config, value.getAsInt()) : Outcome.BAD;
        }
        if (option instanceof DoubleConfig) {
            DoubleConfig config = (DoubleConfig) option;
            return fits(value, config.getMinDoubleValue(), config.getMaxDoubleValue(), false) ? set(config, value.getAsDouble()) : Outcome.BAD;
        }
        if (option instanceof ColorConfig) {
            ColorConfig config = (ColorConfig) option;
            int color;
            if (value.isNumber() && value.getAsDouble() == Math.rint(value.getAsDouble())) color = (int) value.getAsLong();
            else if (value.isString() && value.getAsString().trim().matches("(#|0x|0X)?[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?"))
                color = (int) Long.parseLong(value.getAsString().trim().replaceFirst("^(#|0x|0X)", ""), 16);
            else return Outcome.BAD;
            if (color == config.getIntegerValue()) return Outcome.SAME;
            config.setValueFromInt(color);
            return Outcome.APPLIED;
        }
        if (option instanceof OptionConfig) {
            return value.isString() ? setOption((OptionConfig<?>) option, value.getAsString().trim()) : Outcome.BAD;
        }
        if (option instanceof StringConfig) {
            return value.isString() ? set((StringConfig) option, value.getAsString()) : Outcome.BAD;
        }
        return Outcome.BAD;
    }

    private static <T extends OptionConfigValue> Outcome setOption(OptionConfig<T> config, String name) {
        for (T allowed : config.getAllowedValues()) {
            if (allowed.getName().equalsIgnoreCase(name)) return set(config, allowed);
        }
        return Outcome.BAD;
    }

    /** Whether a value is a number of the option's kind within its limits. */
    private static boolean fits(JsonPrimitive value, double min, double max, boolean whole) {
        if (!value.isNumber()) return false;
        double number = value.getAsDouble();
        if (Double.isNaN(number) || Double.isInfinite(number) || number < min || number > max) return false;
        return !whole || number == Math.rint(number);
    }

    private static <T> Outcome set(BaseGenericConfig<T> config, T value) {
        if (Objects.equals(config.getValue(), value)) return Outcome.SAME;
        return config.setValue(value) ? Outcome.APPLIED : Outcome.BAD;
    }

    @Override
    public List<String> keyNames() {
        return new ArrayList<>(hotkeys.keySet());
    }

    @Override
    public boolean keyModified(String name) {
        return hotkeys.get(name).isModified();
    }

    @Override
    public List<Integer> keys(String name) {
        IntArrayList list = new IntArrayList();
        hotkeys.get(name).getKeysToList(list);
        return new ArrayList<>(list);
    }

    @Override
    public void setKeys(String name, List<Integer> keys) {
        hotkeys.get(name).setValueFromString(Keys.writeKeysToString(new IntArrayList(keys), ",", Keys::charAsStorageString));
    }
}
