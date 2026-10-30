package com.useleess.litematicaupgraded.malilib.action;

import com.useleess.litematicaupgraded.malilib.input.ActionResult;

public interface Action
{
    /**
     * Runs the action/task code
     * @param ctx the common context data for the action
     * @return the result of running the action
     */
    ActionResult execute(ActionContext ctx);
}
