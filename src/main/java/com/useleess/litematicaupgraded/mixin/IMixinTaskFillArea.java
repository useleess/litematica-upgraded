package com.useleess.litematicaupgraded.mixin;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.useleess.litematicaupgraded.litematica.scheduler.tasks.TaskFillArea;

/** What a fill / replace / delete task was asked to do. */
@Mixin(TaskFillArea.class)
public interface IMixinTaskFillArea
{
    @Accessor(value = "fillState", remap = false)
    IBlockState litematicaUpgradedFillState();

    @Nullable
    @Accessor(value = "replaceState", remap = false)
    IBlockState litematicaUpgradedReplaceState();

    @Accessor(value = "removeEntities", remap = false)
    boolean litematicaUpgradedRemoveEntities();
}
