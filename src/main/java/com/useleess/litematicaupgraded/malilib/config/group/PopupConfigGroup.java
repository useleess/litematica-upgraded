package com.useleess.litematicaupgraded.malilib.config.group;

import java.util.List;
import javax.annotation.Nullable;

import com.useleess.litematicaupgraded.malilib.config.option.ConfigInfo;
import com.useleess.litematicaupgraded.malilib.util.data.ModInfo;

public class PopupConfigGroup extends BaseConfigGroup
{
    public PopupConfigGroup(ModInfo modInfo, String name, List<ConfigInfo> configs)
    {
        super(modInfo, name, configs);
    }

    public PopupConfigGroup(ModInfo modInfo, String name, String nameTranslationKey,
                            @Nullable String commentTranslationKey, Object... commentArgs)
    {
        super(modInfo, name, nameTranslationKey, commentTranslationKey, commentArgs);
    }
}
