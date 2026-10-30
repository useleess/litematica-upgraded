package com.useleess.litematicaupgraded.litematica.scheduler.tasks;

import java.util.Collection;

import net.minecraft.init.Blocks;

import com.useleess.litematicaupgraded.malilib.util.data.EnabledCondition;
import com.useleess.litematicaupgraded.malilib.util.position.BlockPos;
import com.useleess.litematicaupgraded.malilib.util.world.BlockState;
import com.useleess.litematicaupgraded.litematica.config.Configs;
import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.materials.IMaterialList;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;
import com.useleess.litematicaupgraded.litematica.selection.SelectionBox;
import com.useleess.litematicaupgraded.litematica.util.value.BlockInfoListType;
import com.useleess.litematicaupgraded.litematica.world.SchematicWorldHandler;
import com.useleess.litematicaupgraded.litematica.world.WorldSchematic;

public class TaskCountBlocksPlacement extends TaskCountBlocksMaterialList
{
    protected final SchematicPlacement schematicPlacement;
    protected final WorldSchematic worldSchematic;
    protected final boolean ignoreState;

    public TaskCountBlocksPlacement(SchematicPlacement schematicPlacement, IMaterialList materialList)
    {
        super(materialList, "litematica.gui.label.task_name.material_list");

        this.ignoreState = Configs.Generic.MATERIAL_LIST_IGNORE_BLOCK_STATE.getBooleanValue();
        this.worldSchematic = SchematicWorldHandler.getSchematicWorld();
        this.schematicPlacement = schematicPlacement;

        Collection<SelectionBox> boxes = schematicPlacement.getSubRegionBoxes(EnabledCondition.ENABLED).values();

        // Filter/clamp the boxes to intersect with the render layer
        if (materialList.getMaterialListType() == BlockInfoListType.RENDER_LAYERS)
        {
            this.addPerChunkBoxes(boxes, DataManager.getRenderLayerRange());
        }
        else
        {
            this.addPerChunkBoxes(boxes);
        }

        this.updateInfoHudLinesMissingChunks(this.requiredChunks);
    }

    @Override
    public boolean canExecute()
    {
        return super.canExecute() && this.worldSchematic != null;
    }

    @Override
    protected void countAtPosition(BlockPos pos)
    {
        BlockState stateSchematic = BlockState.of(this.worldSchematic.getBlockState(pos).getActualState(this.worldSchematic, pos));

        if (stateSchematic.getBlock() != Blocks.AIR)
        {
            BlockState stateClient = BlockState.of(this.worldClient.getBlockState(pos).getActualState(this.worldClient, pos));

            this.countsTotal.addTo(stateSchematic, 1);

            if (stateClient.getBlock() == Blocks.AIR)
            {
                this.countsMissing.addTo(stateSchematic, 1);
            }
            else if ((this.ignoreState          && stateClient.getBlock() != stateSchematic.getBlock()) ||
                     (this.ignoreState == false && stateClient != stateSchematic))
            {
                this.countsMissing.addTo(stateSchematic, 1);
                this.countsMismatch.addTo(stateSchematic, 1);
            }
        }
    }
}
