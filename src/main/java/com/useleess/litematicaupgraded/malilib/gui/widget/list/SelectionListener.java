package com.useleess.litematicaupgraded.malilib.gui.widget.list;

import javax.annotation.Nullable;

public interface SelectionListener<T>
{
    void onSelectionChange(@Nullable T entry);
}
