package com.useleess.litematicaupgraded.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.useleess.litematicaupgraded.litematica.scheduler.tasks.TaskProcessChunkBase;
import com.useleess.litematicaupgraded.remote.RemoteTasks;

/** A save, fill or delete goes through the server part when the server has it on: the task waits for the server instead of reading chunks itself. */
@Mixin(TaskProcessChunkBase.class)
public abstract class MixinTaskProcessChunkBase
{
    @Inject(method = "execute", remap = false, at = @At("HEAD"), cancellable = true)
    private void litematicaUpgradedExecute(CallbackInfoReturnable<Boolean> cir)
    {
        Boolean result = RemoteTasks.execute((TaskProcessChunkBase) (Object) this);

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
}
