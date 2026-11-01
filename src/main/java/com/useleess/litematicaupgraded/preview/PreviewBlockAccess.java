package com.useleess.litematicaupgraded.preview;

import javax.annotation.Nullable;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Biomes;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldType;
import net.minecraft.world.biome.Biome;

/** The blocks of the preview model as a world the block renderer can ask: full bright light, plains colours, air everywhere else. */
final class PreviewBlockAccess implements IBlockAccess {
    private static final IBlockState AIR = Blocks.AIR.getDefaultState();
    private final Long2ObjectOpenHashMap<IBlockState> blocks = new Long2ObjectOpenHashMap<>();

    void put(BlockPos pos, IBlockState state) {
        blocks.put(pos.toLong(), state);
    }

    @Nullable
    @Override
    public TileEntity getTileEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getCombinedLight(BlockPos pos, int lightValue) {
        return PreviewMath.FULL_BRIGHT;
    }

    @Override
    public IBlockState getBlockState(BlockPos pos) {
        IBlockState state = blocks.get(pos.toLong());
        return state != null ? state : AIR;
    }

    @Override
    public boolean isAirBlock(BlockPos pos) {
        return !blocks.containsKey(pos.toLong());
    }

    @Override
    public Biome getBiome(BlockPos pos) {
        return Biomes.PLAINS;
    }

    @Override
    public int getStrongPower(BlockPos pos, EnumFacing direction) {
        return 0;
    }

    @Override
    public WorldType getWorldType() {
        return WorldType.DEFAULT;
    }

    @Override
    public boolean isSideSolid(BlockPos pos, EnumFacing side, boolean defaultValue) {
        IBlockState state = blocks.get(pos.toLong());
        return state != null ? state.isSideSolid(this, pos, side) : defaultValue;
    }
}
