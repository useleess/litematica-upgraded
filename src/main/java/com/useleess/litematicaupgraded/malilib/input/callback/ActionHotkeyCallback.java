package com.useleess.litematicaupgraded.malilib.input.callback;

import com.useleess.litematicaupgraded.malilib.action.Action;
import com.useleess.litematicaupgraded.malilib.action.ActionContext;
import com.useleess.litematicaupgraded.malilib.input.ActionResult;
import com.useleess.litematicaupgraded.malilib.input.KeyAction;
import com.useleess.litematicaupgraded.malilib.input.KeyBind;

public class ActionHotkeyCallback implements HotkeyCallback
{
    protected final Action action;

    public ActionHotkeyCallback(Action action)
    {
        this.action = action;
    }

    @Override
    public ActionResult onKeyAction(KeyAction action, KeyBind key)
    {
        return this.action.execute(ActionContext.COMMON);
    }
}
