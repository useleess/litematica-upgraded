package com.useleess.litematicaupgraded.mixin;

import com.google.common.collect.ArrayListMultimap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.useleess.litematicaupgraded.litematica.scheduler.tasks.TaskPasteSchematicPerChunkBase;
import com.useleess.litematicaupgraded.litematica.util.value.ReplaceBehavior;
import com.useleess.litematicaupgraded.malilib.util.position.ChunkPos;
import com.useleess.litematicaupgraded.malilib.util.position.IntBoundingBox;

/** What a paste task was asked to do. */
@Mixin(TaskPasteSchematicPerChunkBase.class)
public interface IMixinTaskPasteSchematicPerChunkBase
{
    @Accessor(value = "boxesInChunks", remap = false)
    ArrayListMultimap<ChunkPos, IntBoundingBox> litematicaUpgradedBoxesInChunks();

    @Accessor(value = "changedBlockOnly", remap = false)
    boolean litematicaUpgradedChangedBlockOnly();

    @Accessor(value = "replace", remap = false)
    ReplaceBehavior litematicaUpgradedReplace();
}
