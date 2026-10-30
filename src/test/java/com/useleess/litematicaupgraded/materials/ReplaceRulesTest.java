package com.useleess.litematicaupgraded.materials;

import static org.junit.Assert.assertEquals;

import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.EnumFacing;

import org.junit.BeforeClass;
import org.junit.Test;

/** The material list's Replace keeps every property both blocks share and drops the rest. */
public class ReplaceRulesTest {
    @BeforeClass
    public static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    public void stairsKeepFacingHalfAndShape() {
        IBlockState oak = Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.WEST)
            .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.TOP).withProperty(BlockStairs.SHAPE, BlockStairs.EnumShape.OUTER_LEFT);
        IBlockState stone = ReplaceRules.withSharedProperties(oak, Blocks.STONE_BRICK_STAIRS);
        assertEquals(Blocks.STONE_BRICK_STAIRS, stone.getBlock());
        assertEquals(EnumFacing.WEST, stone.getValue(BlockStairs.FACING));
        assertEquals(BlockStairs.EnumHalf.TOP, stone.getValue(BlockStairs.HALF));
        assertEquals(BlockStairs.EnumShape.OUTER_LEFT, stone.getValue(BlockStairs.SHAPE));
        // and back again: the same state as before
        assertEquals(oak, ReplaceRules.withSharedProperties(stone, Blocks.OAK_STAIRS));
    }

    @Test
    public void propertiesTheNewBlockLacksAreDropped() {
        IBlockState stairs = Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.EAST);
        assertEquals(Blocks.STONE.getDefaultState(), ReplaceRules.withSharedProperties(stairs, Blocks.STONE));
        assertEquals(Blocks.OAK_STAIRS.getDefaultState(), ReplaceRules.withSharedProperties(Blocks.STONE.getDefaultState(), Blocks.OAK_STAIRS));
    }

    @Test
    public void axisAndHalfCarryOverBetweenDifferentBlocks() {
        IBlockState log = Blocks.LOG.getDefaultState().withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.X);
        assertEquals(BlockLog.EnumAxis.X, ReplaceRules.withSharedProperties(log, Blocks.LOG2).getValue(BlockLog.LOG_AXIS));
        // a variant the new block does not have (spruce planks -> log2 knows only acacia and dark oak) stays at its default
        IBlockState planks = Blocks.PLANKS.getDefaultState().withProperty(BlockPlanks.VARIANT, BlockPlanks.EnumType.SPRUCE);
        assertEquals(Blocks.LOG2.getDefaultState(), ReplaceRules.withSharedProperties(planks, Blocks.LOG2));
        IBlockState topSlab = Blocks.STONE_SLAB.getDefaultState().withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.TOP);
        assertEquals(BlockSlab.EnumBlockHalf.TOP, ReplaceRules.withSharedProperties(topSlab, Blocks.WOODEN_SLAB).getValue(BlockSlab.HALF));
    }
}
