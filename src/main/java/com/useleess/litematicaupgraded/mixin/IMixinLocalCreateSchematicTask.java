package com.useleess.litematicaupgraded.mixin;

import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import com.useleess.litematicaupgraded.litematica.scheduler.task.LocalCreateSchematicTask;
import com.useleess.litematicaupgraded.litematica.schematic.SchematicSaveSettings;
import com.useleess.litematicaupgraded.litematica.schematic.container.BlockContainer;
import com.useleess.litematicaupgraded.litematica.schematic.data.EntityData;
import com.useleess.litematicaupgraded.litematica.selection.SelectionBox;
import com.useleess.litematicaupgraded.malilib.util.data.tag.CompoundData;
import com.useleess.litematicaupgraded.malilib.util.position.BlockPos;
import com.useleess.litematicaupgraded.malilib.util.world.BlockState;

/** The data a save collects, which the server-side save fills in. */
@Mixin(LocalCreateSchematicTask.class)
public interface IMixinLocalCreateSchematicTask
{
    @Accessor(value = "settings", remap = false)
    SchematicSaveSettings litematicaUpgradedSettings();

    @Accessor(value = "subRegions", remap = false)
    ImmutableMap<String, SelectionBox> litematicaUpgradedSubRegions();

    @Accessor(value = "blockContainers", remap = false)
    Map<String, BlockContainer> litematicaUpgradedBlockContainers();

    @Accessor(value = "blockEntityMaps", remap = false)
    Map<String, Map<BlockPos, CompoundData>> litematicaUpgradedBlockEntityMaps();

    @Accessor(value = "entityLists", remap = false)
    Map<String, List<EntityData>> litematicaUpgradedEntityLists();

    @Accessor(value = "totalBlocks", remap = false)
    long litematicaUpgradedTotalBlocks();

    @Accessor(value = "totalBlocks", remap = false)
    void litematicaUpgradedSetTotalBlocks(long value);

    @Accessor(value = "totalEntities", remap = false)
    int litematicaUpgradedTotalEntities();

    @Accessor(value = "totalEntities", remap = false)
    void litematicaUpgradedSetTotalEntities(int value);

    @Accessor(value = "totalBlockEntities", remap = false)
    long litematicaUpgradedTotalBlockEntities();

    @Accessor(value = "totalBlockEntities", remap = false)
    void litematicaUpgradedSetTotalBlockEntities(long value);

    @Invoker(value = "createBlockContainer", remap = false)
    BlockContainer litematicaUpgradedCreateBlockContainer(SelectionBox box);

    @Invoker(value = "shouldSaveBlock", remap = false)
    boolean litematicaUpgradedShouldSaveBlock(BlockState state, BlockPos.MutBlockPos pos);
}
