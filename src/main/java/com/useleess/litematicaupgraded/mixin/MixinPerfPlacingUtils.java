package com.useleess.litematicaupgraded.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import com.useleess.litematicaupgraded.litematica.schematic.SchematicRegion;
import com.useleess.litematicaupgraded.litematica.schematic.container.BlockContainer;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SubRegionPlacement;
import com.useleess.litematicaupgraded.litematica.schematic.util.SchematicPlacingUtils;
import com.useleess.litematicaupgraded.litematica.util.value.ReplaceBehavior;
import com.useleess.litematicaupgraded.litematica.world.WorldSchematic;
import com.useleess.litematicaupgraded.malilib.util.position.BlockPos;
import com.useleess.litematicaupgraded.malilib.util.position.ChunkPos;
import com.useleess.litematicaupgraded.malilib.util.world.BlockState;
import com.useleess.litematicaupgraded.perf.PerfConfigs;
import com.useleess.litematicaupgraded.perf.PerfStats;

/**
 * Copying a placement into Litematica's schematic world visits every cell of the region in a chunk, air included, and each
 * cell costs several lookups (block entity map, chunk map, block entity checks). When the target chunk is still completely
 * empty (freshly loaded, which is the normal case) an air cell can never change anything, so those cells are handed to the
 * loop as structure void, which it already skips. From Litematica Turbo.
 */
@Mixin(value = SchematicPlacingUtils.class, remap = false)
public abstract class MixinPerfPlacingUtils
{
    @Unique
    private static final BlockState PERF_VOID = BlockState.of(Blocks.STRUCTURE_VOID.getDefaultState());
    /** {skip air: 0/1, cells skipped, start nanos} of the copy running on this thread. */
    @Unique
    private static final ThreadLocal<long[]> PERF_COPY = new ThreadLocal<long[]>();

    @Inject(method = "placeBlocksWithinChunk", at = @At("HEAD"))
    private static void perf$checkEmptyChunk(World world, ChunkPos chunkPos, String regionName, SchematicRegion region, BlockPos origin,
                                             SchematicPlacement schematicPlacement, SubRegionPlacement placement, ReplaceBehavior replace,
                                             boolean notifyNeighbors, CallbackInfoReturnable<Boolean> cir)
    {
        boolean skip = false;

        if (PerfConfigs.FAST_COPY.getBooleanValue() && world instanceof WorldSchematic)
        {
            Chunk chunk = world.getChunk(chunkPos.getX(), chunkPos.getZ());
            skip = chunk != null && chunk.isEmptyBetween(0, 255);
        }

        PERF_COPY.set(new long[] { skip ? 1 : 0, 0, System.nanoTime() });
    }

    @Inject(method = "placeBlocksWithinChunk", at = @At("RETURN"))
    private static void perf$copyDone(World world, ChunkPos chunkPos, String regionName, SchematicRegion region, BlockPos origin,
                                      SchematicPlacement schematicPlacement, SubRegionPlacement placement, ReplaceBehavior replace,
                                      boolean notifyNeighbors, CallbackInfoReturnable<Boolean> cir)
    {
        long[] state = PERF_COPY.get();
        PERF_COPY.remove();

        if (state != null)
        {
            PerfStats.recordCopy(state[0] == 1, state[1], System.nanoTime() - state[2]);
        }
    }

    @Redirect(method = "placeBlocksWithinChunk", expect = 1, at = @At(value = "INVOKE", target = "Lcom/useleess/litematicaupgraded/litematica/schematic/container/BlockContainer;getBlockState(III)Lcom/useleess/litematicaupgraded/malilib/util/world/BlockState;"))
    private static BlockState perf$skipAirIntoEmptyChunk(BlockContainer container, int x, int y, int z)
    {
        BlockState state = container.getBlockState(x, y, z);
        long[] copy = PERF_COPY.get();

        if (copy != null && copy[0] == 1 && state.getBlock() == Blocks.AIR)
        {
            copy[1]++;
            return PERF_VOID;
        }

        return state;
    }
}
