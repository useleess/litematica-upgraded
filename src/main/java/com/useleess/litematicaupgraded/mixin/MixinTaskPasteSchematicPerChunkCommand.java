package com.useleess.litematicaupgraded.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.useleess.litematicaupgraded.litematica.scheduler.tasks.TaskPasteSchematicPerChunkCommand;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.GameWrap;
import com.useleess.litematicaupgraded.remote.RemoteTasks;

/** A command paste goes through the server part when the server has it on. */
@Mixin(TaskPasteSchematicPerChunkCommand.class)
public abstract class MixinTaskPasteSchematicPerChunkCommand
{
    @Inject(method = "execute", remap = false, at = @At("HEAD"), cancellable = true)
    private void litematicaUpgradedExecute(CallbackInfoReturnable<Boolean> cir)
    {
        Boolean result = RemoteTasks.executePaste((TaskPasteSchematicPerChunkCommand) (Object) this);

        if (result != null)
        {
            cir.setReturnValue(result);
        }
    }

    @Inject(method = "stop", remap = false, at = @At("HEAD"))
    private void litematicaUpgradedStop(CallbackInfo ci)
    {
        RemoteTasks.stopped(this);
    }

    /** The paste did not use commands, so the command feedback was not switched off and needs no switching on. */
    @Redirect(method = "stop", remap = false, at = @At(value = "INVOKE", remap = false, target = "Lcom/useleess/litematicaupgraded/malilib/util/game/wrap/GameWrap;sendCommand(Ljava/lang/String;)Z"))
    private boolean litematicaUpgradedSendCommand(String command)
    {
        return RemoteTasks.isRemote(this) || GameWrap.sendCommand(command);
    }
}
