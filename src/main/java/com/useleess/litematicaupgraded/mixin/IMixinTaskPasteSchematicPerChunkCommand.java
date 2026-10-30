package com.useleess.litematicaupgraded.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.useleess.litematicaupgraded.litematica.scheduler.tasks.TaskPasteSchematicPerChunkCommand;

/** The count the paste task reports when it is done. */
@Mixin(TaskPasteSchematicPerChunkCommand.class)
public interface IMixinTaskPasteSchematicPerChunkCommand
{
    @Accessor(value = "sentCommandsTotal", remap = false)
    void litematicaUpgradedSetSentCommandsTotal(int total);
}
