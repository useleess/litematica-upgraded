package com.useleess.litematicaupgraded.malilib.config.serialization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import com.useleess.litematicaupgraded.malilib.config.option.BooleanAndDoubleConfig;
import com.useleess.litematicaupgraded.malilib.config.option.BooleanAndFileConfig;
import com.useleess.litematicaupgraded.malilib.config.option.BooleanAndIntConfig;
import com.useleess.litematicaupgraded.malilib.config.option.BooleanConfig;
import com.useleess.litematicaupgraded.malilib.config.option.ColorConfig;
import com.useleess.litematicaupgraded.malilib.config.option.DoubleConfig;
import com.useleess.litematicaupgraded.malilib.config.option.DualColorConfig;
import com.useleess.litematicaupgraded.malilib.config.option.FileConfig;
import com.useleess.litematicaupgraded.malilib.config.option.HotkeyConfig;
import com.useleess.litematicaupgraded.malilib.config.option.HotkeyedBooleanConfig;
import com.useleess.litematicaupgraded.malilib.config.option.IntegerConfig;
import com.useleess.litematicaupgraded.malilib.config.option.OptionConfig;
import com.useleess.litematicaupgraded.malilib.config.option.StringConfig;
import com.useleess.litematicaupgraded.malilib.config.option.Vec2dConfig;
import com.useleess.litematicaupgraded.malilib.config.option.Vec2iConfig;
import com.useleess.litematicaupgraded.malilib.config.option.list.BlackWhiteListConfig;
import com.useleess.litematicaupgraded.malilib.config.option.list.ValueListConfig;
import com.useleess.litematicaupgraded.malilib.config.value.OptionConfigValue;
import com.useleess.litematicaupgraded.malilib.util.data.json.JsonSerializers;

public class JsonConfigSerializers
{
    public static JsonElement serializeBooleanConfig(BooleanConfig config)
    {
        return new JsonPrimitive(config.getValueForSerialization());
    }

    public static JsonElement serializeIntegerConfig(IntegerConfig config)
    {
        return new JsonPrimitive(config.getValueForSerialization());
    }

    public static JsonElement serializeDoubleConfig(DoubleConfig config)
    {
        return new JsonPrimitive(config.getValueForSerialization());
    }

    public static JsonElement serializeStringConfig(StringConfig config)
    {
        return new JsonPrimitive(config.getValueForSerialization());
    }

    public static JsonElement serializeColorConfig(ColorConfig config)
    {
        return new JsonPrimitive(config.getValueForSerialization().toString());
    }

    public static JsonElement serializeFileConfig(FileConfig config)
    {
        return new JsonPrimitive(config.getValueForSerialization().toAbsolutePath().toString());
    }

    public static JsonElement serializeHotkeyConfig(HotkeyConfig config)
    {
        return config.getKeyBind().getAsJsonElement();
    }

    public static JsonElement serializeHotkeyedBooleanConfig(HotkeyedBooleanConfig config)
    {
        JsonObject obj = new JsonObject();
        obj.add("enabled", new JsonPrimitive(config.getValueForSerialization()));
        obj.add("hotkey", config.getKeyBind().getAsJsonElement());
        return obj;
    }

    public static JsonElement serializeDualColorConfig(DualColorConfig config)
    {
        return JsonSerializers.serializeDualColorValue(config.getValueForSerialization());
    }

    public static JsonElement serializeVec2dConfig(Vec2dConfig config)
    {
        return JsonSerializers.serializeVec2dValue(config.getValueForSerialization());
    }

    public static JsonElement serializeVec2iConfig(Vec2iConfig config)
    {
        return JsonSerializers.serializeVec2iValue(config.getValueForSerialization());
    }

    public static JsonElement serializeBooleanAndIntConfig(BooleanAndIntConfig config)
    {
        return JsonSerializers.serializeBooleanAndIntValue(config.getValueForSerialization());
    }

    public static JsonElement serializeBooleanAndDoubleConfig(BooleanAndDoubleConfig config)
    {
        return JsonSerializers.serializeBooleanAndDoubleValue(config.getValueForSerialization());
    }

    public static JsonElement serializeBooleanAndFileConfig(BooleanAndFileConfig config)
    {
        return JsonSerializers.serializeBooleanAndFileValue(config.getValueForSerialization());
    }

    public static <T extends OptionConfigValue> JsonElement serializeOptionListConfig(OptionConfig<T> config)
    {
        return JsonSerializers.serializeOptionListValue(config.getValueForSerialization());
    }

    public static <T> JsonElement serializeValueListConfig(ValueListConfig<T> config)
    {
        return JsonSerializers.serializeValueListAsString(config.getValueForSerialization(), config.getToStringConverter());
    }

    public static <T> JsonElement serializeBlackWhiteListConfig(BlackWhiteListConfig<T> config)
    {
        return JsonSerializers.serializeBlackWhiteList(config.getValueForSerialization());
    }
}
