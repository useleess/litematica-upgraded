package com.useleess.litematicaupgraded.malilib.event;

import com.useleess.litematicaupgraded.malilib.render.RenderContext;
import com.useleess.litematicaupgraded.malilib.util.ProfilerSectionSupplierSupplier;

public interface PostGameOverlayRenderer extends ProfilerSectionSupplierSupplier
{
    /**
     * Called after the vanilla overlays have been rendered.
     * <br><br>
     * The classes implementing this method should be registered
     * to {@link com.useleess.litematicaupgraded.malilib.event.dispatch.RenderEventDispatcher}.
     * <br><br>
     * Note: The client world and the client player are checked
     * to not be null before this method is called.
     */
    void onPostGameOverlayRender(RenderContext ctx);
}
