package com.useleess.litematicaupgraded.mixin;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.useleess.litematicaupgraded.litematica.scheduler.tasks.TaskBase;

/** The state of a Litematica task that the server-side schematics set from outside. */
@Mixin(TaskBase.class)
public interface IMixinTaskBase
{
    @Accessor(value = "finished", remap = false)
    boolean litematicaUpgradedFinished();

    @Accessor(value = "finished", remap = false)
    void litematicaUpgradedSetFinished(boolean finished);

    @Accessor(value = "infoHudLines", remap = false)
    void litematicaUpgradedSetInfoHudLines(List<String> lines);
}
