package com.useleess.litematicaupgraded.malilib.event.dispatch;

import com.useleess.litematicaupgraded.malilib.event.PostGameOverlayRenderer;
import com.useleess.litematicaupgraded.malilib.event.PostItemTooltipRenderer;
import com.useleess.litematicaupgraded.malilib.event.PostScreenRenderer;
import com.useleess.litematicaupgraded.malilib.event.PostWorldRenderer;

public interface RenderEventDispatcher
{
    /**
     * Registers a renderer which will have its {@link PostGameOverlayRenderer#onPostGameOverlayRender()}
     * method called after the vanilla game overlay rendering is done.
     * @param renderer
     */
    void registerGameOverlayRenderer(PostGameOverlayRenderer renderer);

    /**
     * Registers a renderer which will have its {@link PostItemTooltipRenderer#onPostRenderItemTooltip(net.minecraft.item.ItemStack, int, int)}
     * method called after the vanilla ItemStack tooltip text has been rendered.
     * @param renderer
     */
    void registerTooltipPostRenderer(PostItemTooltipRenderer renderer);

    /**
     * Registers a renderer which will have its {@link PostScreenRenderer#onPostScreenRender(float)}
     * method called after the vanilla screen rendering method has been called.
     * @param renderer
     */
    void registerScreenPostRenderer(PostScreenRenderer renderer);

    /**
     * Registers a renderer which will have its {@link PostWorldRenderer#onPostWorldRender(float)}
     * method called after the vanilla world rendering is done.
     * @param renderer
     */
    void registerWorldPostRenderer(PostWorldRenderer renderer);
}
