// Redstone-aware build order after Schematica Upgraded's RedstoneOrder / PrinterBuildOrder (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.printer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockButton;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockHopper;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockObserver;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.BlockRailBase;
import net.minecraft.block.BlockRedstoneComparator;
import net.minecraft.block.BlockRedstoneDiode;
import net.minecraft.block.BlockRedstoneTorch;
import net.minecraft.block.BlockRedstoneWire;
import net.minecraft.block.state.IBlockState;

/**
 * Blocks a component depends on come first, components that would fire or update early come last:
 * tier 0 plain blocks, 1 attached blocks (levers, buttons, torches, rails), 2 redstone wiring (wire, repeaters,
 * comparators), 3 the components that fire (pistons, observers, dispensers, droppers, hoppers).
 */
public final class PrinterOrder {
    /** A block held back for lower tiers is let through after this many ticks, so a missing item never stalls the build. */
    public static final int PATIENCE = 40;

    private PrinterOrder() {}

    public static int tier(IBlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockPistonBase || block instanceof BlockObserver || block instanceof BlockDispenser || block instanceof BlockHopper) return 3;
        if (block instanceof BlockRedstoneWire || block instanceof BlockRedstoneDiode || block instanceof BlockRedstoneComparator) return 2;
        if (block instanceof BlockLever || block instanceof BlockButton || block instanceof BlockRedstoneTorch || block instanceof BlockRailBase) return 1;
        return 0;
    }

    /** Whether a block of this tier may be placed now: nothing of a lower tier is waiting, or it has waited long enough. */
    public static boolean mayPlace(int tier, int lowestWaitingTier, long waitedTicks) {
        return tier <= lowestWaitingTier || waitedTicks >= PATIENCE;
    }
}
