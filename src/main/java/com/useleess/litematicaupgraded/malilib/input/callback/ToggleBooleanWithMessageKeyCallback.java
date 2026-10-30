package com.useleess.litematicaupgraded.malilib.input.callback;

import javax.annotation.Nullable;

import com.useleess.litematicaupgraded.malilib.action.Action;
import com.useleess.litematicaupgraded.malilib.action.ActionContext;
import com.useleess.litematicaupgraded.malilib.action.builtin.BooleanToggleAction;
import com.useleess.litematicaupgraded.malilib.config.option.BooleanContainingConfig;
import com.useleess.litematicaupgraded.malilib.input.ActionResult;
import com.useleess.litematicaupgraded.malilib.input.KeyAction;
import com.useleess.litematicaupgraded.malilib.input.KeyBind;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageHelpers.BooleanConfigMessageFactory;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageOutput;

public class ToggleBooleanWithMessageKeyCallback implements HotkeyCallback
{
    protected final BooleanContainingConfig<?> config;
    @Nullable protected final BooleanConfigMessageFactory messageFactory;

    public ToggleBooleanWithMessageKeyCallback(BooleanContainingConfig<?> config)
    {
        this(config, null);
    }

    public ToggleBooleanWithMessageKeyCallback(BooleanContainingConfig<?> config,
                                               @Nullable BooleanConfigMessageFactory messageFactory)
    {
        this.config = config;
        this.messageFactory = messageFactory;
    }

    @Override
    public ActionResult onKeyAction(KeyAction action, KeyBind key)
    {
        MessageOutput messageOutput = key.getSettings().getMessageType();
        // The action needs to be created here to capture the MessageOutput from the KeyBind
        Action toggleAction = BooleanToggleAction.of(this.config, this.messageFactory, () -> messageOutput);

        return toggleAction.execute(ActionContext.COMMON);
    }
}
