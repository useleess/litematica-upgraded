package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;

import com.useleess.litematicaupgraded.malilib.render.text.StyledTextLine;

public class EntityEntryWidget extends BaseDataListEntryWidget<Class<? extends Entity>>
{
    public EntityEntryWidget(Class<? extends Entity> data, DataListEntryWidgetData constructData)
    {
        super(data, constructData);
        this.setText(StyledTextLine.parseFirstLine(EntityList.getKey(data).toString()));
    }
}
