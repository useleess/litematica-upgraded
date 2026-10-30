// Fixing a placed block's adjustable state by clicking it, after Schematica Upgraded's StateClicks (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.printer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockRedstoneComparator;
import net.minecraft.block.BlockRedstoneRepeater;
import net.minecraft.block.state.IBlockState;

/** Properties a placement cannot choose but a click cycles: a repeater's delay, a comparator's mode, a lever's switch. */
public final class StateClicks {
    private StateClicks() {}

    /** Whether clicks can change something of this block. */
    public static boolean adjustable(Block block) {
        return block instanceof BlockRedstoneRepeater || block instanceof BlockRedstoneComparator || block instanceof BlockLever;
    }

    /**
     * The clicks on the world block that give it the schematic state: 0 when nothing is to fix, -1 when clicks cannot
     * fix it (another block, or other properties differ).
     */
    public static int clicks(IBlockState real, IBlockState target) {
        Block block = target.getBlock();
        if (!adjustable(block) || !StateMatch.switchedPair(real.getBlock(), block)) return -1;
        if (!StateMatch.built(real, target)) return -1;
        if (block instanceof BlockRedstoneRepeater) {
            return Math.floorMod(target.getValue(BlockRedstoneRepeater.DELAY) - real.getValue(BlockRedstoneRepeater.DELAY), 4);
        }
        if (block instanceof BlockRedstoneComparator) {
            return real.getValue(BlockRedstoneComparator.MODE) == target.getValue(BlockRedstoneComparator.MODE) ? 0 : 1;
        }
        return real.getValue(BlockLever.POWERED).equals(target.getValue(BlockLever.POWERED)) ? 0 : 1;
    }
}
