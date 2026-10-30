package com.useleess.litematicaupgraded.perf;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.useleess.litematicaupgraded.litematica.render.schematic.ChunkCacheSchematic;

/** The "client world" a complete-copy piece compares with: all air, so every schematic block is built and no overlay. */
public class EmptyChunkView extends ChunkCacheSchematic {
    public EmptyChunkView(World world, BlockPos pos) {
        super(world, pos, 0);
        this.empty = true;
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        return Blocks.AIR.getDefaultState();
    }
}
