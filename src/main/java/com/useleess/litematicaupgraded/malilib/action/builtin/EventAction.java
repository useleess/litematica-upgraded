package com.useleess.litematicaupgraded.malilib.action.builtin;

import com.useleess.litematicaupgraded.malilib.action.Action;
import com.useleess.litematicaupgraded.malilib.action.ActionContext;
import com.useleess.litematicaupgraded.malilib.input.ActionResult;
import com.useleess.litematicaupgraded.malilib.listener.EventListener;

public class EventAction implements Action
{
    protected final EventListener listener;

    public EventAction(EventListener listener)
    {
        this.listener = listener;
    }

    @Override
    public ActionResult execute(ActionContext ctx)
    {
        this.listener.onEvent();
        return ActionResult.SUCCESS;
    }

    public static EventAction of(EventListener listener)
    {
        return new EventAction(listener);
    }
}
