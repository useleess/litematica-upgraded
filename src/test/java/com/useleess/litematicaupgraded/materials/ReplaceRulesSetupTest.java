package com.useleess.litematicaupgraded.materials;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.BeforeClass;
import org.junit.Test;

import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockPlanks;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.block.BlockColored;
import net.minecraft.util.EnumFacing;

/** #14: replacing in Schematic Edit mode keeps how a block was turned and set, never its material. */
public class ReplaceRulesSetupTest {
    @BeforeClass
    public static void boot() {
        Bootstrap.register();
    }

    @Test public void gateKeepsFacingAndOpen() {
        IBlockState oak = Blocks.OAK_FENCE_GATE.getDefaultState().withProperty(BlockFenceGate.FACING, EnumFacing.EAST)
                .withProperty(BlockFenceGate.OPEN, true).withProperty(BlockFenceGate.IN_WALL, true);
        assertSame(Blocks.SPRUCE_FENCE_GATE.getDefaultState().withProperty(BlockFenceGate.FACING, EnumFacing.EAST)
                .withProperty(BlockFenceGate.OPEN, true).withProperty(BlockFenceGate.IN_WALL, true),
                ReplaceRules.keepingSetup(oak, Blocks.SPRUCE_FENCE_GATE.getDefaultState()));
    }

    @Test public void doorKeepsFacingOpenHingeAndHalf() {
        IBlockState oak = Blocks.OAK_DOOR.getDefaultState().withProperty(BlockDoor.FACING, EnumFacing.WEST).withProperty(BlockDoor.OPEN, true)
                .withProperty(BlockDoor.HINGE, BlockDoor.EnumHingePosition.RIGHT).withProperty(BlockDoor.HALF, BlockDoor.EnumDoorHalf.UPPER);
        IBlockState iron = ReplaceRules.keepingSetup(oak, Blocks.IRON_DOOR.getDefaultState());
        assertSame(Blocks.IRON_DOOR.getDefaultState().withProperty(BlockDoor.FACING, EnumFacing.WEST).withProperty(BlockDoor.OPEN, true)
                .withProperty(BlockDoor.HINGE, BlockDoor.EnumHingePosition.RIGHT).withProperty(BlockDoor.HALF, BlockDoor.EnumDoorHalf.UPPER), iron);
        assertEquals(-1, ReplaceRules.otherHalfDy(iron));
        assertSame(iron.withProperty(BlockDoor.HALF, BlockDoor.EnumDoorHalf.LOWER), ReplaceRules.otherHalf(iron));
    }

    @Test public void stairsKeepFacingAndHalfAlsoOnSlabs() {
        IBlockState stairs = Blocks.OAK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.SOUTH)
                .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.TOP);
        assertSame(Blocks.STONE_BRICK_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.SOUTH)
                .withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.TOP), ReplaceRules.keepingSetup(stairs, Blocks.STONE_BRICK_STAIRS.getDefaultState()));
        assertEquals(BlockSlab.EnumBlockHalf.TOP, ReplaceRules.keepingSetup(stairs, Blocks.STONE_SLAB.getDefaultState()).getValue(BlockSlab.HALF));
    }

    @Test public void logKeepsItsAxis() {
        IBlockState log = Blocks.LOG.getDefaultState().withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.X);
        assertEquals(BlockLog.EnumAxis.X, ReplaceRules.keepingSetup(log, Blocks.LOG2.getDefaultState()).getValue(BlockLog.LOG_AXIS));
    }

    @Test public void materialIsNeverCopied() {
        IBlockState spruce = Blocks.PLANKS.getDefaultState().withProperty(BlockPlanks.VARIANT, BlockPlanks.EnumType.SPRUCE);
        assertSame(spruce, ReplaceRules.keepingSetup(Blocks.PLANKS.getDefaultState(), spruce));
        IBlockState red = Blocks.WOOL.getDefaultState().withProperty(BlockColored.COLOR, EnumDyeColor.RED);
        assertSame(red, ReplaceRules.keepingSetup(Blocks.WOOL.getDefaultState(), red));
    }

    @Test public void airAndUnrelatedBlocksChangeNothing() {
        IBlockState gate = Blocks.OAK_FENCE_GATE.getDefaultState();
        assertSame(gate, ReplaceRules.keepingSetup(Blocks.AIR.getDefaultState(), gate));
        assertSame(Blocks.STONE.getDefaultState(), ReplaceRules.keepingSetup(Blocks.OAK_DOOR.getDefaultState(), Blocks.STONE.getDefaultState()));
    }

    @Test public void onlyTwoBlockTallBlocksHaveAnOtherHalf() {
        assertEquals(1, ReplaceRules.otherHalfDy(Blocks.OAK_DOOR.getDefaultState()));
        assertEquals(1, ReplaceRules.otherHalfDy(Blocks.DOUBLE_PLANT.getDefaultState().withProperty(BlockDoublePlant.HALF, BlockDoublePlant.EnumBlockHalf.LOWER)));
        assertEquals(0, ReplaceRules.otherHalfDy(Blocks.TRAPDOOR.getDefaultState().withProperty(BlockTrapDoor.HALF, BlockTrapDoor.DoorHalf.TOP)));
        assertEquals(0, ReplaceRules.otherHalfDy(Blocks.OAK_STAIRS.getDefaultState()));
        assertNull(ReplaceRules.otherHalf(Blocks.STONE.getDefaultState()));
    }
}
