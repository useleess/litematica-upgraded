package com.useleess.litematicaupgraded.litematica.scheduler.tasks;

import net.minecraft.block.state.IBlockState;

import com.useleess.litematicaupgraded.malilib.util.position.BlockPos;
import com.useleess.litematicaupgraded.malilib.util.world.BlockState;
import com.useleess.litematicaupgraded.litematica.materials.IMaterialList;
import com.useleess.litematicaupgraded.litematica.selection.AreaSelection;

public class TaskCountBlocksArea extends TaskCountBlocksMaterialList
{
    protected final AreaSelection selection;

    public TaskCountBlocksArea(AreaSelection selection, IMaterialList materialList)
    {
        super(materialList, "litematica.gui.label.task_name.area_analyzer");

        this.selection = selection;
        this.addPerChunkBoxes(selection.getAllSelectionBoxes());
        this.updateInfoHudLinesMissingChunks(this.requiredChunks);
    }

    @Override
    protected void countAtPosition(BlockPos pos)
    {
        IBlockState stateClient = this.worldClient.getBlockState(pos).getActualState(this.worldClient, pos);
        this.countsTotal.addTo(BlockState.of(stateClient), 1);
    }
}
