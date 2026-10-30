package com.useleess.litematicaupgraded.malilib;

import java.util.List;
import com.google.common.collect.ImmutableList;

import com.useleess.litematicaupgraded.malilib.input.Hotkey;
import com.useleess.litematicaupgraded.malilib.input.HotkeyCategory;
import com.useleess.litematicaupgraded.malilib.input.HotkeyProvider;
import com.useleess.litematicaupgraded.malilib.util.ListUtils;
import com.useleess.litematicaupgraded.malilib.util.data.ModInfo;

public class MaLiLibHotkeyProvider implements HotkeyProvider
{
    static final MaLiLibHotkeyProvider INSTANCE = new MaLiLibHotkeyProvider();

    private MaLiLibHotkeyProvider()
    {
    }

    @Override
    public List<? extends Hotkey> getAllHotkeys()
    {
        return ListUtils.getAppendedList(MaLiLibConfigs.Hotkeys.FUNCTIONAL_HOTKEYS,
                                         MaLiLibConfigs.Debug.HOTKEYS);
    }

    @Override
    public List<HotkeyCategory> getHotkeysByCategories()
    {
        ModInfo modInfo = MaLiLibReference.MOD_INFO;

        return ImmutableList.of(
                new HotkeyCategory(modInfo, "malilib.hotkeys.category.debug_hotkeys"  , MaLiLibConfigs.Debug.HOTKEYS),
                new HotkeyCategory(modInfo, "malilib.hotkeys.category.generic_hotkeys", MaLiLibConfigs.Hotkeys.FUNCTIONAL_HOTKEYS));
    }
}
