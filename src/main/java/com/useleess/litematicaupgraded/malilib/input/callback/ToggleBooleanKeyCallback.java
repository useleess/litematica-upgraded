package com.useleess.litematicaupgraded.malilib.input.callback;

import com.useleess.litematicaupgraded.malilib.input.ActionResult;
import com.useleess.litematicaupgraded.malilib.input.KeyAction;
import com.useleess.litematicaupgraded.malilib.input.KeyBind;
import com.useleess.litematicaupgraded.malilib.util.data.BooleanStorage;

public class ToggleBooleanKeyCallback implements HotkeyCallback
{
    protected final BooleanStorage config;

    public ToggleBooleanKeyCallback(BooleanStorage config)
    {
        this.config = config;
    }

    @Override
    public ActionResult onKeyAction(KeyAction action, KeyBind key)
    {
        this.config.toggleBooleanValue();
        return ActionResult.SUCCESS;
    }
}
