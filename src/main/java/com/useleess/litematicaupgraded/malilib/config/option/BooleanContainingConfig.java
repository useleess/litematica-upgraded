package com.useleess.litematicaupgraded.malilib.config.option;

import com.useleess.litematicaupgraded.malilib.util.data.BooleanStorageWithDefault;

public interface BooleanContainingConfig<T> extends BooleanStorageWithDefault, ConfigOption<T>, OverridableConfig<T>
{
}
