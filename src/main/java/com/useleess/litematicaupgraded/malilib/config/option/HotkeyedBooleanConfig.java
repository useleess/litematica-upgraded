package com.useleess.litematicaupgraded.malilib.config.option;

import javax.annotation.Nullable;

import com.useleess.litematicaupgraded.malilib.action.Action;
import com.useleess.litematicaupgraded.malilib.action.builtin.BooleanToggleAction;
import com.useleess.litematicaupgraded.malilib.input.Hotkey;
import com.useleess.litematicaupgraded.malilib.input.KeyBind;
import com.useleess.litematicaupgraded.malilib.input.KeyBindImpl;
import com.useleess.litematicaupgraded.malilib.input.KeyBindSettings;
import com.useleess.litematicaupgraded.malilib.input.callback.HotkeyCallback;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageHelpers.BooleanConfigMessageFactory;
import com.useleess.litematicaupgraded.malilib.util.StringUtils;
import com.useleess.litematicaupgraded.malilib.util.data.ModInfo;

public class HotkeyedBooleanConfig extends BooleanConfig implements Hotkey
{
    protected final KeyBind keyBind;
    protected Action toggleAction;

    public HotkeyedBooleanConfig(String name, boolean defaultValue, String defaultHotkey)
    {
        this(name, defaultValue, defaultHotkey, name);
    }

    public HotkeyedBooleanConfig(String name, boolean defaultValue, String defaultHotkey, KeyBindSettings settings)
    {
        this(name, defaultValue, defaultHotkey, settings, name, name);
    }

    public HotkeyedBooleanConfig(String name, boolean defaultValue, String defaultHotkey,
                                 @Nullable String commentTranslationKey, Object... commentArgs)
    {
        this(name, defaultValue, defaultHotkey, StringUtils.splitCamelCase(name),
             commentTranslationKey, commentArgs);
    }

    public HotkeyedBooleanConfig(String name, boolean defaultValue, String defaultHotkey,
                                 String prettyName, @Nullable String commentTranslationKey, Object... commentArgs)
    {
        this(name, defaultValue, defaultHotkey, KeyBindSettings.INGAME_DEFAULT,
             prettyName, commentTranslationKey, commentArgs);
    }

    public HotkeyedBooleanConfig(String name, boolean defaultValue, String defaultHotkey,
                                 KeyBindSettings settings, String prettyName,
                                 @Nullable String commentTranslationKey, Object... commentArgs)
    {
        super(name, defaultValue, prettyName, commentTranslationKey, commentArgs);

        this.keyBind = KeyBindImpl.fromStorageString(defaultHotkey, settings);

        this.setSpecialToggleMessageFactory(null);
        this.keyBind.cacheSavedValue();
    }

    @Override
    public KeyBind getKeyBind()
    {
        return this.keyBind;
    }

    public Action getToggleAction()
    {
        return this.toggleAction;
    }

    /**
     * This will replace the default hotkey callback with the variant that takes in the message factory
     */
    public void setSpecialToggleMessageFactory(@Nullable BooleanConfigMessageFactory messageFactory)
    {
        this.toggleAction = BooleanToggleAction.of(this, messageFactory, this.keyBind.getSettings()::getMessageType);
        this.keyBind.setCallback(HotkeyCallback.of(this.toggleAction));
    }

    public void setHotkeyCallback(HotkeyCallback callback)
    {
        this.keyBind.setCallback(callback);
    }

    @Override
    public void setModInfo(ModInfo modInfo)
    {
        super.setModInfo(modInfo);

        this.keyBind.setNameTranslationKey(this.nameTranslationKey);
        this.keyBind.setModInfo(modInfo);
    }

    @Override
    public void setNameTranslationKey(String key)
    {
        this.keyBind.setNameTranslationKey(key);

        super.setNameTranslationKey(key);
    }

    @Override
    public boolean isModified()
    {
        return super.isModified() || this.keyBind.isModified();
    }

    @Override
    public boolean isDirty()
    {
        return super.isDirty() || this.keyBind.isDirty();
    }

    @Override
    public void cacheSavedValue()
    {
        super.cacheSavedValue();
        this.keyBind.cacheSavedValue();
    }

    @Override
    public void resetToDefault()
    {
        super.resetToDefault();
        this.keyBind.resetToDefault();
    }

    public void loadHotkeyedBooleanValueFromConfig(boolean booleanValue)
    {
        this.value = booleanValue;
        this.cacheSavedValue();
        this.updateEffectiveValue();
        this.onValueLoaded(this.effectiveValue);
    }
}
