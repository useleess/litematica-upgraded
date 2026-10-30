package com.useleess.litematicaupgraded.litematica.scheduler;

import com.useleess.litematicaupgraded.malilib.gui.util.GuiUtils;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.GameWrap;
import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.schematic.verifier.SchematicVerifierManager;
import com.useleess.litematicaupgraded.litematica.util.EasyPlaceUtils;

public class ClientTickHandler implements com.useleess.litematicaupgraded.malilib.event.ClientTickHandler
{
    protected int tickCounter;

    @Override
    public void onClientTick()
    {
        if (GameWrap.getClientPlayer() == null || GameWrap.getClientWorld() == null)
        {
            return;
        }

        DataManager.getRenderLayerRange().followPlayerIfEnabled(GameWrap.getClientPlayer());
        DataManager.getSchematicPlacementManager().processQueuedChunks();
        TaskScheduler.getInstanceClient().runTasks();

        if ((this.tickCounter) % 10 == 0)
        {
            SchematicVerifierManager.INSTANCE.scheduleReChecks();
        }

        if (GuiUtils.noScreenOpen())
        {
            EasyPlaceUtils.easyPlaceOnUseTick();
        }

        ++this.tickCounter;
    }
}
