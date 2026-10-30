package com.useleess.litematicaupgraded.malilib.action.builtin;

import java.util.function.Supplier;
import javax.annotation.Nullable;

import com.useleess.litematicaupgraded.malilib.config.option.BooleanContainingConfig;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageHelpers.BooleanConfigMessageFactory;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageOutput;

public class BooleanToggleAction extends BaseBooleanConfigAction
{
    protected BooleanToggleAction(BooleanContainingConfig<?> config,
                                  @Nullable BooleanConfigMessageFactory messageFactory,
                                  @Nullable Supplier<MessageOutput> messageTypeSupplier)
    {
        super(config, BooleanContainingConfig::toggleBooleanValue, messageFactory, messageTypeSupplier);
    }

    public static BooleanToggleAction of(BooleanContainingConfig<?> config,
                                         @Nullable BooleanConfigMessageFactory messageFactory,
                                         @Nullable Supplier<MessageOutput> messageTypeSupplier)
    {
        return new BooleanToggleAction(config, messageFactory, messageTypeSupplier);
    }
}