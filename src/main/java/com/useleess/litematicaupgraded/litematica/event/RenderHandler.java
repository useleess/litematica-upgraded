package com.useleess.litematicaupgraded.litematica.event;

import com.useleess.litematicaupgraded.malilib.event.PostGameOverlayRenderer;
import com.useleess.litematicaupgraded.malilib.event.PostWorldRenderer;
import com.useleess.litematicaupgraded.malilib.gui.util.GuiUtils;
import com.useleess.litematicaupgraded.malilib.render.RenderContext;
import com.useleess.litematicaupgraded.litematica.config.Configs;
import com.useleess.litematicaupgraded.litematica.config.Hotkeys;
import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.render.LitematicaRenderer;
import com.useleess.litematicaupgraded.litematica.render.OverlayRenderer;
import com.useleess.litematicaupgraded.litematica.render.infohud.InfoHud;
import com.useleess.litematicaupgraded.litematica.render.infohud.ToolHud;
import com.useleess.litematicaupgraded.litematica.scheduler.TaskScheduler;
import com.useleess.litematicaupgraded.litematica.scheduler.task.SetSchematicPreviewTask;
import com.useleess.litematicaupgraded.litematica.tool.ToolMode;

public class RenderHandler implements PostGameOverlayRenderer, PostWorldRenderer
{
    @Override
    public void onPostWorldRender(RenderContext ctx, float tickDelta)
    {
        if (Configs.Visuals.MAIN_RENDERING_TOGGLE.getBooleanValue())
        {
            boolean invert = Hotkeys.INVERT_SCHEMATIC_RENDER_STATE.isHeld();

            if (Configs.Visuals.SCHEMATIC_RENDERING.getBooleanValue() != invert &&
                Configs.Generic.BETTER_RENDER_ORDER.getBooleanValue() == false)
            {
                LitematicaRenderer.getInstance().renderSchematicWorld(ctx, tickDelta);
            }

            OverlayRenderer.getInstance().renderBoxes(ctx, tickDelta);

            if (Configs.InfoOverlays.VERIFIER_OVERLAY_RENDERING.getBooleanValue())
            {
                OverlayRenderer.getInstance().renderSchematicVerifierMismatches(ctx, tickDelta);
            }

            if (Configs.Visuals.RENDER_COLLIDING_BLOCK_AT_CURSOR.getBooleanValue())
            {
                boolean render = Configs.Visuals.SCHEMATIC_BLOCKS_RENDERING.getBooleanValue() &&
                                 Configs.Visuals.SCHEMATIC_RENDERING.getBooleanValue() != invert;

                if (render)
                {
                    OverlayRenderer.getInstance().renderHoveredSchematicBlock(ctx, tickDelta);
                }
            }

            if (DataManager.getToolMode() == ToolMode.SCHEMATIC_EDIT)
            {
                OverlayRenderer.getInstance().renderSchematicRebuildTargetingOverlay(tickDelta, ctx);
            }
        }
    }

    @Override
    public void onPostGameOverlayRender(RenderContext ctx)
    {
        if (Configs.Visuals.MAIN_RENDERING_TOGGLE.getBooleanValue())
        {
            // The Info HUD renderers can decide if they want to be rendered in GUIs
            InfoHud.getInstance().renderHud(ctx);

            if (GuiUtils.noScreenOpen())
            {
                ToolHud.getInstance().renderHud(ctx);
                OverlayRenderer.getInstance().renderHoverInfo(ctx);

                SetSchematicPreviewTask task = TaskScheduler.getInstanceClient().getFirstTaskOfType(SetSchematicPreviewTask.class);

                if (task != null)
                {
                    OverlayRenderer.getInstance().renderPreviewFrame(ctx);
                }
            }
        }
    }
}
