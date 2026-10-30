package com.useleess.litematicaupgraded.litematica.scheduler.tasks;

import java.util.List;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import com.useleess.litematicaupgraded.malilib.util.game.wrap.GameWrap;
import com.useleess.litematicaupgraded.malilib.util.position.ChunkPos;
import com.useleess.litematicaupgraded.malilib.util.position.LayerRange;
import com.useleess.litematicaupgraded.malilib.util.world.BlockState;
import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.materials.IMaterialList;
import com.useleess.litematicaupgraded.litematica.materials.MaterialListEntry;
import com.useleess.litematicaupgraded.litematica.materials.MaterialListUtils;
import com.useleess.litematicaupgraded.litematica.render.infohud.InfoHud;
import com.useleess.litematicaupgraded.litematica.util.value.BlockInfoListType;
import com.useleess.litematicaupgraded.litematica.world.SchematicWorldRenderingNotifier;

public abstract class TaskCountBlocksMaterialList extends TaskCountBlocksBase
{
    protected final Object2LongOpenHashMap<BlockState> countsMissing = new Object2LongOpenHashMap<>();
    protected final Object2LongOpenHashMap<BlockState> countsMismatch = new Object2LongOpenHashMap<>();
    protected final IMaterialList materialList;
    protected final LayerRange layerRange;

    protected TaskCountBlocksMaterialList(IMaterialList materialList, String nameOnHud)
    {
        super(nameOnHud);

        this.materialList = materialList;

        if (materialList.getMaterialListType() == BlockInfoListType.ALL)
        {
            this.layerRange = new LayerRange(SchematicWorldRenderingNotifier.INSTANCE);
        }
        else
        {
            this.layerRange = DataManager.getRenderLayerRange();
        }
    }

    @Override
    protected boolean processChunk(ChunkPos pos)
    {
        this.countBlocksInChunkRespectingLayerRange(pos, this.layerRange);
        return true;
    }

    @Override
    protected void onStop()
    {
        if (this.finished && GameWrap.getClientPlayer() != null)
        {
            List<MaterialListEntry> list = MaterialListUtils.getMaterialList(
                    this.countsTotal, this.countsMissing, this.countsMismatch);
            this.materialList.setMaterialListEntries(list);
        }

        InfoHud.getInstance().removeInfoHudRenderer(this, false);

        this.notifyListener();
    }
}
