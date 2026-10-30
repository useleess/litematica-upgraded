package com.useleess.litematicaupgraded.malilib.event;

import com.useleess.litematicaupgraded.malilib.gui.util.ScreenContext;
import com.useleess.litematicaupgraded.malilib.util.ProfilerSectionSupplierSupplier;

public interface PostScreenRenderer extends ProfilerSectionSupplierSupplier
{
    /**
     * Called after vanilla screen rendering
     * <br><br>
     * The classes implementing this method should be registered
     * to {@link com.useleess.litematicaupgraded.malilib.event.dispatch.RenderEventDispatcher}.
     * <br><br>
     * Note: The client world and the client player are checked
     * to not be null before this method is called.
     */
    void onPostScreenRender(ScreenContext ctx, float tickDelta);
}
