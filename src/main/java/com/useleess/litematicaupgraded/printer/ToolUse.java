// Blocks built with a tool instead of an item, after the behavior of Buildprint's assist tools, by HackerRouter, 2026 (Schematica Upgraded, MIT).
package com.useleess.litematicaupgraded.printer;

import net.minecraft.block.BlockDirt;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemFlintAndSteel;
import net.minecraft.item.ItemHoe;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * Farmland is tilled from the dirt or grass already there with a hoe, and a nether portal is lit with flint and steel on
 * the obsidian below its lowest blocks (a portal has no item). Farmland on nothing gets its dirt placed first as usual.
 */
public final class ToolUse {
    private ToolUse() {}

    /** The block to click (on its top face) that builds the schematic block with a tool; null when none applies. */
    public static BlockPos click(World world, IBlockState target, IBlockState real, BlockPos pos) {
        if (!PrinterConfigs.USE_TOOLS.getBooleanValue()) return null;
        if (target.getBlock() == Blocks.FARMLAND && tillable(world, real, pos)) return pos;
        if (target.getBlock() == Blocks.PORTAL && world.isAirBlock(pos) && world.getBlockState(pos.down()).getBlock() == Blocks.OBSIDIAN) {
            return pos.down();
        }
        return null;
    }

    /** Dirt, grass or a grass path with room above, which a hoe turns into farmland (ItemHoe.onItemUse). */
    public static boolean tillable(World world, IBlockState real, BlockPos pos) {
        return (real.getBlock() == Blocks.GRASS || real.getBlock() == Blocks.GRASS_PATH
            || real.getBlock() == Blocks.DIRT && real.getValue(BlockDirt.VARIANT) == BlockDirt.DirtType.DIRT) && world.isAirBlock(pos.up());
    }

    /** Whether a mismatch is one that a tool fixes, so that the world block is not mined. */
    public static boolean fixable(World world, IBlockState target, IBlockState real, BlockPos pos) {
        return PrinterConfigs.USE_TOOLS.getBooleanValue() && target.getBlock() == Blocks.FARMLAND && tillable(world, real, pos);
    }

    /** Whether the stack is the tool that builds this block. */
    static boolean accepts(IBlockState target, ItemStack stack) {
        if (stack.isEmpty()) return false;
        return target.getBlock() == Blocks.FARMLAND ? stack.getItem() instanceof ItemHoe : stack.getItem() instanceof ItemFlintAndSteel;
    }
}
