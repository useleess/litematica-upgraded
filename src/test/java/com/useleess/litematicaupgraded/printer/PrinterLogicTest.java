package com.useleess.litematicaupgraded.printer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.block.BlockDirectional;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockRedstoneComparator;
import net.minecraft.block.BlockRedstoneRepeater;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.EnumFacing;

import org.junit.BeforeClass;
import org.junit.Test;

/** The printer's pure logic: build order tiers, what counts as the schematic state, how many clicks fix a state, rotation candidates. */
public class PrinterLogicTest {
    @BeforeClass
    public static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    public void firingComponentsComeAfterWiringAfterPlainBlocks() {
        int stone = PrinterOrder.tier(Blocks.STONE.getDefaultState());
        int lever = PrinterOrder.tier(Blocks.LEVER.getDefaultState());
        int repeater = PrinterOrder.tier(Blocks.UNPOWERED_REPEATER.getDefaultState());
        int piston = PrinterOrder.tier(Blocks.PISTON.getDefaultState());
        int observer = PrinterOrder.tier(Blocks.OBSERVER.getDefaultState());
        assertTrue(stone < lever && lever < repeater && repeater < piston);
        assertEquals(piston, observer);
        assertEquals(piston, PrinterOrder.tier(Blocks.DROPPER.getDefaultState()));
    }

    @Test
    public void aHigherTierWaitsForLowerOnesOnlyForAWhile() {
        assertTrue(PrinterOrder.mayPlace(0, 0, 0));
        assertTrue(PrinterOrder.mayPlace(3, Integer.MAX_VALUE, 0));
        assertFalse(PrinterOrder.mayPlace(3, 0, 5));
        assertTrue(PrinterOrder.mayPlace(3, 0, PrinterOrder.PATIENCE));
    }

    @Test
    public void placementStateIgnoresWhatTheWorldMakes() {
        IBlockState observer = Blocks.OBSERVER.getDefaultState().withProperty(BlockDirectional.FACING, EnumFacing.EAST);
        assertTrue(StateMatch.placedMatches(observer.withProperty(net.minecraft.block.BlockObserver.POWERED, true), observer));
        assertFalse(StateMatch.placedMatches(observer.withProperty(BlockDirectional.FACING, EnumFacing.WEST), observer));
        assertFalse(StateMatch.placedMatches(Blocks.STONE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState()));
    }

    @Test
    public void litTwinsCountAsTheSameBlock() {
        assertTrue(StateMatch.switchedPair(Blocks.UNPOWERED_REPEATER, Blocks.POWERED_REPEATER));
        assertTrue(StateMatch.switchedPair(Blocks.REDSTONE_TORCH, Blocks.UNLIT_REDSTONE_TORCH));
        assertFalse(StateMatch.switchedPair(Blocks.STONE, Blocks.REDSTONE_TORCH));
    }

    @Test
    public void repeaterDelayAndComparatorModeAndLeverTakeClicks() {
        IBlockState repeater = Blocks.UNPOWERED_REPEATER.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.EAST);
        assertEquals(2, StateClicks.clicks(repeater, repeater.withProperty(BlockRedstoneRepeater.DELAY, 3)));
        assertEquals(1, StateClicks.clicks(repeater.withProperty(BlockRedstoneRepeater.DELAY, 4), repeater));
        assertEquals(0, StateClicks.clicks(repeater, repeater));
        assertEquals(-1, StateClicks.clicks(repeater, repeater.withProperty(BlockHorizontal.FACING, EnumFacing.WEST)));
        IBlockState comparator = Blocks.UNPOWERED_COMPARATOR.getDefaultState();
        assertEquals(1, StateClicks.clicks(comparator, comparator.withProperty(BlockRedstoneComparator.MODE, BlockRedstoneComparator.Mode.SUBTRACT)));
        IBlockState lever = Blocks.LEVER.getDefaultState();
        assertEquals(1, StateClicks.clicks(lever, lever.withProperty(BlockLever.POWERED, true)));
        assertEquals(-1, StateClicks.clicks(Blocks.STONE.getDefaultState(), Blocks.STONE.getDefaultState()));
    }

    @Test
    public void rotationCandidatesCoverTheFourDirectionsAndUpAndDown() {
        PrinterLook[] looks = PrinterLook.candidates(37f);
        assertEquals(6, looks.length);
        assertEquals(90f, looks[4].pitch, 0f);
        assertEquals(-90f, looks[5].pitch, 0f);
        for (int i = 0; i < 4; i++) assertEquals(0f, looks[i].pitch, 0f);
    }

    @Test
    public void aSideBlockIsNotTheFakeStandZoneOfAHighBlock() {
        net.minecraft.util.math.BlockPos target = new net.minecraft.util.math.BlockPos(0, 100, 0);
        assertEquals(FakeStand.Zone.ABOVE, FakeStand.zone(target, 0.5, 103, 0.5, 1.62));
        assertEquals(FakeStand.Zone.SIDE, FakeStand.zone(target, 5.5, 101, 0.5, 1.62));
        assertEquals(FakeStand.Zone.BELOW, FakeStand.zone(target, 0.5, 97, 0.5, 1.62));
    }
}
