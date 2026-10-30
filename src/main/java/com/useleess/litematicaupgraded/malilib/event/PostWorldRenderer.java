package com.useleess.litematicaupgraded.malilib.event;

import com.useleess.litematicaupgraded.malilib.render.RenderContext;
import com.useleess.litematicaupgraded.malilib.util.ProfilerSectionSupplierSupplier;

public interface PostWorldRenderer extends ProfilerSectionSupplierSupplier
{
    /**
     * Called after vanilla world rendering
     * <br><br>
     * The classes implementing this method should be registered
     * to {@link com.useleess.litematicaupgraded.malilib.event.dispatch.RenderEventDispatcher}.
     * <br><br>
     * Note: The client world and the client player are checked
     * to not be null before this method is called.
     */
    void onPostWorldRender(RenderContext ctx, float tickDelta);
}
