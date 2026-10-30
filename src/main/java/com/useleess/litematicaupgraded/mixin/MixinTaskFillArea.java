package com.useleess.litematicaupgraded.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.useleess.litematicaupgraded.litematica.scheduler.tasks.TaskFillArea;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.GameWrap;
import com.useleess.litematicaupgraded.remote.RemoteTasks;

/** A fill or delete that went through the server part did not switch the command feedback off, so it is not switched on either. */
@Mixin(TaskFillArea.class)
public abstract class MixinTaskFillArea
{
    @Redirect(method = "onStop", remap = false, at = @At(value = "INVOKE", remap = false, target = "Lcom/useleess/litematicaupgraded/malilib/util/game/wrap/GameWrap;sendCommand(Ljava/lang/String;)Z"))
    private boolean litematicaUpgradedSendCommand(String command)
    {
        return RemoteTasks.isRemote(this) || GameWrap.sendCommand(command);
    }
}
