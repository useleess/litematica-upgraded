package com.useleess.litematicaupgraded.litematica.materials;

import com.useleess.litematicaupgraded.malilib.overlay.message.MessageDispatcher;
import com.useleess.litematicaupgraded.malilib.util.StringUtils;
import com.useleess.litematicaupgraded.litematica.scheduler.TaskScheduler;
import com.useleess.litematicaupgraded.litematica.scheduler.tasks.TaskCountBlocksArea;
import com.useleess.litematicaupgraded.litematica.selection.AreaSelection;

public class MaterialListAreaAnalyzer extends MaterialListBase
{
    private final AreaSelection selection;

    public MaterialListAreaAnalyzer(AreaSelection selection)
    {
        super();

        this.selection = selection;
    }

    @Override
    public String getName()
    {
        return this.selection.getName();
    }

    @Override
    public String getTitle()
    {
        return StringUtils.translate("litematica.title.screen.material_list.area_analyzer", this.getName());
    }

    @Override
    public void reCreateMaterialList()
    {
        TaskCountBlocksArea task = new TaskCountBlocksArea(this.selection, this);
        TaskScheduler.getInstanceClient().scheduleTask(task, 20);
        MessageDispatcher.generic(1000).translate("litematica.message.scheduled_task_added");
    }
}
