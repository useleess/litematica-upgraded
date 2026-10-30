package com.useleess.litematicaupgraded.malilib.input;

import com.useleess.litematicaupgraded.malilib.event.PrioritizedEventHandler;

public interface MouseMoveHandler extends PrioritizedEventHandler
{
    /**
     * Called when the mouse is moved
     */
    void onMouseMove(int mouseX, int mouseY);
}
