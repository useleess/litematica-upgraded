// Doors and tall plants: one item makes both parts, the upper part comes with the lower one and is never placed on its own.
package com.useleess.litematicaupgraded.printer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.state.IBlockState;

public final class TwoBlockParts {
    private TwoBlockParts() {}

    /** Whether the state is the upper part of a door or tall plant: the item placed one block lower makes it. */
    public static boolean isUpperPart(IBlockState state) {
        Block block = state.getBlock();
        if (block instanceof BlockDoor) return state.getValue(BlockDoor.HALF) == BlockDoor.EnumDoorHalf.UPPER;
        if (block instanceof BlockDoublePlant) return state.getValue(BlockDoublePlant.HALF) == BlockDoublePlant.EnumBlockHalf.UPPER;
        return false;
    }
}
