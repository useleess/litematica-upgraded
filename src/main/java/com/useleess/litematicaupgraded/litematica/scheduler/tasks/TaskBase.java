package com.useleess.litematicaupgraded.litematica.scheduler.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;

import com.useleess.litematicaupgraded.malilib.listener.TaskCompletionListener;
import com.useleess.litematicaupgraded.malilib.util.StringUtils;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.WorldWrap;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.EntityWrap;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.GameWrap;
import com.useleess.litematicaupgraded.malilib.util.position.ChunkPos;
import com.useleess.litematicaupgraded.litematica.config.Configs;
import com.useleess.litematicaupgraded.litematica.render.infohud.IInfoHudRenderer;
import com.useleess.litematicaupgraded.litematica.render.infohud.InfoHud;
import com.useleess.litematicaupgraded.litematica.render.infohud.RenderPhase;
import com.useleess.litematicaupgraded.litematica.scheduler.ITask;
import com.useleess.litematicaupgraded.litematica.scheduler.TaskTimer;
import com.useleess.litematicaupgraded.litematica.util.PositionUtils;

public abstract class TaskBase implements ITask, IInfoHudRenderer
{
    private TaskTimer timer = new TaskTimer(1);

    protected final Minecraft mc;
    protected String name = "";
    protected List<String> infoHudLines = new ArrayList<>();
    protected boolean finished;
    protected boolean printCompletionMessage = true;
    @Nullable private TaskCompletionListener completionListener;

    protected TaskBase()
    {
        this.mc = Minecraft.getMinecraft();
    }

    @Override
    public TaskTimer getTimer()
    {
        return this.timer;
    }

    @Override
    public String getDisplayName()
    {
        return this.name;
    }

    @Override
    public void createTimer(int interval)
    {
        this.timer = new TaskTimer(interval);
    }

    public void disableCompletionMessage()
    {
        this.printCompletionMessage = false;
    }

    public void setCompletionListener(@Nullable TaskCompletionListener listener)
    {
        this.completionListener = listener;
    }

    @Override
    public boolean canExecute()
    {
        return this.mc.world != null;
    }

    @Override
    public boolean shouldRemove()
    {
        return this.canExecute() == false;
    }

    @Override
    public void init()
    {
    }

    @Override
    public void stop()
    {
        this.notifyListener();
        InfoHud.getInstance().removeInfoHudRenderer(this, false);
    }

    protected void notifyListener()
    {
        if (this.completionListener != null)
        {
            this.mc.addScheduledTask(() ->
            {
                if (this.finished)
                {
                    this.completionListener.onTaskCompleted();
                }
                else
                {
                    this.completionListener.onTaskAborted();
                }
            });
        }
    }

    protected boolean areSurroundingChunksLoaded(ChunkPos pos, WorldClient world, int radius)
    {
        if (radius <= 0)
        {
            return WorldWrap.isClientChunkLoaded(pos.x, pos.z, world);
        }

        int chunkX = pos.x;
        int chunkZ = pos.z;

        for (int cx = chunkX - radius; cx <= chunkX + radius; ++cx)
        {
            for (int cz = chunkZ - radius; cz <= chunkZ + radius; ++cz)
            {
                if (WorldWrap.isClientChunkLoaded(cx, cz, world) == false)
                {
                    return false;
                }
            }
        }

        return true;
    }

    protected void updateInfoHudLinesMissingChunks(Set<ChunkPos> requiredChunks)
    {
        List<String> hudLines = new ArrayList<>();

        if (GameWrap.getClientPlayer() != null && requiredChunks.isEmpty() == false)
        {
            List<ChunkPos> list = new ArrayList<>(requiredChunks);
            PositionUtils.CHUNK_POS_COMPARATOR.setReferencePosition(EntityWrap.getPlayerBlockPos());
            PositionUtils.CHUNK_POS_COMPARATOR.setClosestFirst(true);
            list.sort(PositionUtils.CHUNK_POS_COMPARATOR);

            hudLines.add(StringUtils.translate("litematica.title.hud.missing_chunks", this.name, requiredChunks.size()));

            int maxLines = Math.min(list.size(), Configs.InfoOverlays.INFO_HUD_MAX_LINES.getIntegerValue());

            for (int i = 0; i < maxLines; ++i)
            {
                ChunkPos pos = list.get(i);
                hudLines.add(String.format("cx: %5d, cz: %5d (x: %d, z: %d)", pos.x, pos.z, pos.x << 4, pos.z << 4));
            }
        }

        this.infoHudLines = hudLines;
    }

    @Override
    public boolean getShouldRenderText(RenderPhase phase)
    {
        return phase == RenderPhase.POST;
    }

    @Override
    public List<String> getText(RenderPhase phase)
    {
        return this.infoHudLines;
    }
}
