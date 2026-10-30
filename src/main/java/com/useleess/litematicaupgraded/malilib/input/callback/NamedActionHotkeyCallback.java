package com.useleess.litematicaupgraded.malilib.input.callback;

import com.useleess.litematicaupgraded.malilib.action.NamedAction;
import com.useleess.litematicaupgraded.malilib.input.ActionResult;
import com.useleess.litematicaupgraded.malilib.input.KeyAction;
import com.useleess.litematicaupgraded.malilib.input.KeyBind;

public class NamedActionHotkeyCallback implements HotkeyCallback
{
    protected final NamedAction action;

    public NamedActionHotkeyCallback(NamedAction action)
    {
        this.action = action;
    }

    @Override
    public ActionResult onKeyAction(KeyAction action, KeyBind key)
    {
        return this.action.execute();
    }
}
