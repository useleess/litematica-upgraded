package com.useleess.litematicaupgraded.printer;

import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemSlab;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The server refuses a click on a top face in the world's top layer (y 255, vanilla's height limit check), but
 * Litematica's Easy Place clicks the target spot itself on its top face for a bottom slab or the second half of a
 * double slab. Such a click is made another way that places the same: on the top of the block below (same spot, same
 * face), or for a bottom slab over nothing, on a side of the spot low down.
 */
public final class TopLayerClick {
    private TopLayerClick() {}

    /** The click Easy Place wanted, made the accepted way; null when it is not a top-face click in the top layer. */
    public static EnumActionResult click(EntityPlayerSP player, WorldClient world, BlockPos pos, EnumFacing side, Vec3d hit, EnumHand hand) {
        PlacementSolver.Solution solution = replacement(world, pos, side, hit, player.getHeldItem(hand));
        if (solution == null) return null;
        return Printer.click(Minecraft.getMinecraft(), player, world, solution, hand, true) ? EnumActionResult.SUCCESS : EnumActionResult.FAIL;
    }

    static PlacementSolver.Solution replacement(World world, BlockPos pos, EnumFacing side, Vec3d hit, ItemStack held) {
        if (side != EnumFacing.UP || pos.getY() < world.getHeight() - 1) return null;
        BlockPos below = pos.down();
        IBlockState under = world.getBlockState(below);
        // clicking a single slab of the held kind below would complete that one instead
        boolean sameSlabBelow = held.getItem() instanceof ItemSlab && under.getBlock() instanceof BlockSlab
            && !((BlockSlab) under.getBlock()).isDouble() && held.getItem() == net.minecraft.item.Item.getItemFromBlock(under.getBlock());
        if (!under.getBlock().isReplaceable(world, below) && !sameSlabBelow) {
            return new PlacementSolver.Solution(below, EnumFacing.UP, new Vec3d(hit.x, pos.getY(), hit.z), null, null);
        }
        if (held.getItem() instanceof ItemSlab && world.getBlockState(pos).getBlock().isReplaceable(world, pos)) {
            return new PlacementSolver.Solution(pos, EnumFacing.NORTH, new Vec3d(hit.x, pos.getY() + 0.25, pos.getZ()), null, null);
        }
        return null;
    }
}
