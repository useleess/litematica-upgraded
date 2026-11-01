package com.useleess.litematicaupgraded.preview;

import com.google.common.collect.ImmutableList;

import com.useleess.litematicaupgraded.malilib.config.option.BooleanConfig;
import com.useleess.litematicaupgraded.malilib.config.option.ConfigOption;

/** The 3D preview's setting, in the "Litematica Upgraded" tab (PrinterConfigs adds it to the tab's list). */
public final class PreviewConfigs {
    /** The turning 3D view of the selected schematic in the schematic browser's info panel. */
    public static final BooleanConfig ENABLED = new BooleanConfig("preview3d", true);

    public static final ImmutableList<ConfigOption<?>> OPTIONS = ImmutableList.of(ENABLED);

    private PreviewConfigs() {}
}
