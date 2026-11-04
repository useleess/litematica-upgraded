// Temporary support blocks for fence gates the schematic has over air or water, by HackerRouter, 2026 (Schematica Upgraded, MIT).
package com.useleess.litematicaupgraded.printer;

import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * In 1.12 a fence gate can only be placed on a solid block (unless Carpet's relaxedBlockPlacement is on: RelaxedPlacement), but once placed it stays when that block goes; builds with
 * gates over air or water (water stoppers in farms) were made with a temporary block under each gate. The printer does
 * the same: a scaffold block under the gate, the gate, then the scaffold is mined away again.
 */
public final class Scaffold {
    /** Cheap full blocks used as scaffolding (item damage 0 gives the default state), the first one carried wins. */
    static final List<Block> MATERIALS = Arrays.asList(Blocks.COBBLESTONE, Blocks.DIRT, Blocks.NETHERRACK, Blocks.STONE, Blocks.PLANKS);

    private Scaffold() {}

    /**
     * Whether the schematic block at pos can only be placed with a scaffold below: a fence gate whose world position is
     * free, over a free position where the schematic has nothing to build (air or fluid).
     */
    static boolean needed(World world, World schematic, BlockPos pos, IBlockState target) {
        if (!(target.getBlock() instanceof BlockFenceGate)) return false;
        if (!world.getBlockState(pos).getBlock().isReplaceable(world, pos) || target.getBlock().canPlaceBlockAt(world, pos)) return false;
        BlockPos below = pos.down();
        if (below.getY() < 0 || !world.getBlockState(below).getBlock().isReplaceable(world, below)) return false;
        IBlockState wanted = schematic.getBlockState(below);
        return wanted.getBlock().isAir(wanted, schematic, below) || FluidPrinter.isFluid(wanted.getBlock());
    }

    /** Whether a scaffold under pos has done its job: the gate above is built. */
    static boolean done(World world, BlockPos scaffold) {
        return world.getBlockState(scaffold.up()).getBlock() instanceof BlockFenceGate;
    }

    /** The scaffold material to place: the first carried one, or null. */
    static Block material(InventoryPlayer inventory) {
        for (Block block : MATERIALS) {
            for (ItemStack stack : inventory.mainInventory) {
                if (!stack.isEmpty() && stack.getItem() instanceof ItemBlock && ((ItemBlock) stack.getItem()).getBlock() == block && stack.getMetadata() == 0) return block;
            }
        }
        return null;
    }
}
