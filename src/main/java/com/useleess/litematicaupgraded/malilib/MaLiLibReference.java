package com.useleess.litematicaupgraded.malilib;

import com.useleess.litematicaupgraded.malilib.util.StringUtils;
import com.useleess.litematicaupgraded.malilib.util.data.ModInfo;

public class MaLiLibReference
{
    public static final String MOD_ID = "malilib";
    /** Our own asset folder: masa's malilib and Litematica ship other pictures at the "malilib" / "litematica" paths. */
    public static final String ASSETS_ID = "litematica_upgraded";
    public static final String MOD_NAME = "MaLiLib";
    public static final String MOD_VERSION = StringUtils.getModVersionString(MOD_ID);

    public static final ModInfo MOD_INFO = new ModInfo(MOD_ID, MOD_NAME);
}
