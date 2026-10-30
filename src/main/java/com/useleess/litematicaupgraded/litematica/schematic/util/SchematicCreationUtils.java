package com.useleess.litematicaupgraded.litematica.schematic.util;

import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.malilib.gui.TextInputScreen;
import com.useleess.litematicaupgraded.malilib.input.ActionResult;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageDispatcher;
import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.data.SchematicHolder;
import com.useleess.litematicaupgraded.litematica.gui.CreateInMemorySchematicScreen;
import com.useleess.litematicaupgraded.litematica.gui.SaveSchematicFromAreaScreen;
import com.useleess.litematicaupgraded.litematica.scheduler.TaskScheduler;
import com.useleess.litematicaupgraded.litematica.scheduler.task.LocalCreateSchematicTask;
import com.useleess.litematicaupgraded.litematica.schematic.LoadedSchematic;
import com.useleess.litematicaupgraded.litematica.schematic.Schematic;
import com.useleess.litematicaupgraded.litematica.schematic.SchematicSaveSettings;
import com.useleess.litematicaupgraded.litematica.schematic.projects.SchematicProject;
import com.useleess.litematicaupgraded.litematica.selection.AreaSelection;
import com.useleess.litematicaupgraded.litematica.selection.AreaSelectionManager;

public class SchematicCreationUtils
{
    public static ActionResult saveSchematic(boolean inMemoryOnly)
    {
        AreaSelectionManager sm = DataManager.getAreaSelectionManager();
        AreaSelection area = sm.getCurrentSelection();

        if (area != null)
        {
            if (DataManager.getSchematicProjectsManager().hasProjectOpen())
            {
                String title = "litematica.title.screen.schematic_vcs.save_new_version";
                SchematicProject project = DataManager.getSchematicProjectsManager().getCurrentProject();
                TextInputScreen screen = new TextInputScreen(title, project.getCurrentVersionName(),
                                                             DataManager.getSchematicProjectsManager()::commitNewVersion);
                BaseScreen.openPopupScreenWithCurrentScreenAsParent(screen);
            }
            else if (inMemoryOnly)
            {
                BaseScreen.openPopupScreenWithCurrentScreenAsParent(new CreateInMemorySchematicScreen(area));
            }
            else
            {
                BaseScreen.openScreenWithParent(new SaveSchematicFromAreaScreen(area));
            }

            return ActionResult.SUCCESS;
        }

        return ActionResult.FAIL;
    }

    public static boolean saveInMemorySchematic(String name, AreaSelection area)
    {
        // TODO Allow changing the settings
        SchematicSaveSettings settings = new SchematicSaveSettings();
        LocalCreateSchematicTask task = new LocalCreateSchematicTask(area, settings,
                                            sch -> onInMemorySchematicCreated(sch, name));
        TaskScheduler.getServerInstanceIfExistsOrClient().scheduleTask(task, 10);

        return true;
    }

    private static void onInMemorySchematicCreated(Schematic schematic, String name)
    {
        SchematicHolder.INSTANCE.addSchematic(new LoadedSchematic(schematic), true);
        MessageDispatcher.success("litematica.message.in_memory_schematic_created", name);
    }
}
