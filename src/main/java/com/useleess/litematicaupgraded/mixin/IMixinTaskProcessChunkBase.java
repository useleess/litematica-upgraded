package com.useleess.litematicaupgraded.mixin;

import com.google.common.collect.ArrayListMultimap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.useleess.litematicaupgraded.litematica.scheduler.tasks.TaskProcessChunkBase;
import com.useleess.litematicaupgraded.malilib.util.position.ChunkPos;
import com.useleess.litematicaupgraded.malilib.util.position.IntBoundingBox;

/** The boxes a chunk-by-chunk task (save, fill, delete) works on, and whether it works on the client world. */
@Mixin(TaskProcessChunkBase.class)
public interface IMixinTaskProcessChunkBase
{
    @Accessor(value = "boxesInChunks", remap = false)
    ArrayListMultimap<ChunkPos, IntBoundingBox> litematicaUpgradedBoxesInChunks();

    @Accessor(value = "isClientWorld", remap = false)
    boolean litematicaUpgradedIsClientWorld();
}
