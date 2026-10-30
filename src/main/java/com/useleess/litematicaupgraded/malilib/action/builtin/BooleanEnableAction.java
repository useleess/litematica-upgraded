package com.useleess.litematicaupgraded.malilib.action.builtin;

import java.util.function.Supplier;
import javax.annotation.Nullable;

import com.useleess.litematicaupgraded.malilib.config.option.BooleanContainingConfig;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageHelpers.BooleanConfigMessageFactory;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageOutput;

public class BooleanEnableAction extends BaseBooleanConfigAction
{
    protected BooleanEnableAction(BooleanContainingConfig<?> config,
                                  @Nullable BooleanConfigMessageFactory messageFactory,
                                  @Nullable Supplier<MessageOutput> messageTypeSupplier)
    {
        super(config, cfg -> cfg.setBooleanValue(true), messageFactory, messageTypeSupplier);
    }

    public static BooleanEnableAction of(BooleanContainingConfig<?> config,
                                         @Nullable BooleanConfigMessageFactory messageFactory,
                                         @Nullable Supplier<MessageOutput> messageTypeSupplier)
    {
        return new BooleanEnableAction(config, messageFactory, messageTypeSupplier);
    }
}