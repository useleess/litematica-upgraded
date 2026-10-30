package com.useleess.litematicaupgraded.litematica;

import com.useleess.litematicaupgraded.malilib.util.StringUtils;
import com.useleess.litematicaupgraded.malilib.util.data.ModInfo;

public class Reference
{
    public static final String MOD_ID = "litematica";
    public static final String MOD_NAME = "Litematica";
    public static final String MOD_VERSION = StringUtils.getModVersionString(MOD_ID);

    public static final ModInfo MOD_INFO = new ModInfo(MOD_ID, MOD_NAME);
}
