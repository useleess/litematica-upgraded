package com.useleess.litematicaupgraded.litematica.gui.widget.list.entry;

import com.useleess.litematicaupgraded.malilib.gui.widget.button.GenericButton;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.BaseDataListEntryWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import com.useleess.litematicaupgraded.malilib.render.text.StyledTextLine;
import com.useleess.litematicaupgraded.litematica.scheduler.ITask;
import com.useleess.litematicaupgraded.litematica.scheduler.TaskScheduler;

public class TaskEntryWidget extends BaseDataListEntryWidget<ITask>
{
    protected final GenericButton removeButton;

    public TaskEntryWidget(ITask data, DataListEntryWidgetData constructData)
    {
        super(data, constructData);

        this.removeButton = GenericButton.create("litematica.gui.button.remove");
        this.removeButton.setActionListener(() -> {
            if (TaskScheduler.getInstanceClient().removeTask(this.getData()) == false)
            {
                TaskScheduler.getInstanceServer().removeTask(this.getData());
            }

            this.listWidget.refreshEntries();
        });

        this.setText(StyledTextLine.parseFirstLine(data.getDisplayName()));
    }

    @Override
    public void reAddSubWidgets()
    {
        super.reAddSubWidgets();

        this.addWidget(this.removeButton);
    }

    @Override
    public void updateSubWidgetPositions()
    {
        super.updateSubWidgetPositions();

        this.removeButton.setRight(this.getRight() - 2);
        this.removeButton.setY(this.getY() + 1);
    }
}
