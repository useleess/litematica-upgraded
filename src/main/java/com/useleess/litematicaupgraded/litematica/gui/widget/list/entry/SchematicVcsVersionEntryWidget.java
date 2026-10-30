package com.useleess.litematicaupgraded.litematica.gui.widget.list.entry;

import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.BaseDataListEntryWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import com.useleess.litematicaupgraded.malilib.render.text.StyledTextLine;
import com.useleess.litematicaupgraded.litematica.schematic.projects.SchematicVersion;

public class SchematicVcsVersionEntryWidget extends BaseDataListEntryWidget<SchematicVersion>
{
    public SchematicVcsVersionEntryWidget(SchematicVersion data, DataListEntryWidgetData constructData)
    {
        super(data, constructData);

        String key = "litematica.label.widget.schematic_vcs.version_entry";
        this.setText(StyledTextLine.translateFirstLine(key, data.getVersion(), data.getName()));
    }

    @Override
    protected boolean isSelected()
    {
        return this.data.getProject().getCurrentVersion() == this.data;
    }
}
