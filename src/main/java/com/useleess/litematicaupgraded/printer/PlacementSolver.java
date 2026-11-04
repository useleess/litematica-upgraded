// Placement by simulation after Schematica Upgraded's PlacementSolver (Lunatrius / HackerRouter / Useleess, MIT; itself after Buildprint's solver).
package com.useleess.litematicaupgraded.printer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.BlockChest;
import net.minecraft.block.BlockDispenser;
import net.minecraft.block.BlockEnderChest;
import net.minecraft.block.BlockFalling;
import net.minecraft.block.BlockFurnace;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.BlockWallSign;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemBlockSpecial;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.useleess.litematicaupgraded.litematica.config.Configs;
import com.useleess.litematicaupgraded.litematica.mixin.IMixinItemBlockSpecial;

/**
 * Every click the player could make (a face of a solid neighbour, at a few heights, with the current rotation or one of
 * the six straight ones, from the player's place or a fake one) is first simulated with the block's own
 * getStateForPlacement, the method the server runs for the same click. The first click that gives the schematic state is used.
 * When no neighbour gives it, the free position itself is clicked, as Easy Place does: the game places a block in a free
 * position it is clicked on, so this needs nothing to click on (servers that refuse it: Easy Place Click Adjacent turns it off).
 */
public final class PlacementSolver {
    public static final class Solution {
        /** The block to click and the face of it. */
        public final BlockPos clicked;
        public final EnumFacing side;
        public final Vec3d hit;
        /** The rotation to report before the click; null keeps the player's. */
        public final PrinterLook look;
        /** The feet position to report before the click (FakeStand); null keeps the player's. */
        public final Vec3d stand;
        /** A click the game's own client would not send (RelaxedPlacement). */
        public final boolean relaxed;

        Solution(BlockPos clicked, EnumFacing side, Vec3d hit, PrinterLook look, Vec3d stand) {
            this(clicked, side, hit, look, stand, false);
        }

        Solution(BlockPos clicked, EnumFacing side, Vec3d hit, PrinterLook look, Vec3d stand, boolean relaxed) {
            this.clicked = clicked; this.side = side; this.hit = hit; this.look = look; this.stand = stand; this.relaxed = relaxed;
        }
    }

    /** What a click is made on: a neighbour's face, the free position itself, or that with the game's own placing rules set aside. */
    private enum Target { NEIGHBOUR, ITSELF, RELAXED }

    private static final float[] SIDE_HEIGHTS = {0.5f, 0.25f, 0.75f};
    /** Click order: the block below first, as a player builds. */
    private static final EnumFacing[] ORDER = {EnumFacing.DOWN, EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.WEST, EnumFacing.EAST, EnumFacing.UP};

    private PlacementSolver() {}

    /** The directions of the neighbours a block can be placed against: not air, fluid or replaceable. */
    public static List<EnumFacing> solidSides(World world, BlockPos pos) {
        List<EnumFacing> sides = new ArrayList<>();
        for (EnumFacing side : ORDER) {
            BlockPos neighbour = pos.offset(side);
            IBlockState state = world.getBlockState(neighbour);
            if (state.getBlock().isAir(state, world, neighbour) || state.getMaterial().isLiquid()
                || state.getBlock().isReplaceable(world, neighbour)) continue;
            sides.add(side);
        }
        return sides;
    }

    /**
     * The click that gives the target state, or null when there is none (no solid neighbour yet, or no rotation of the
     * player gives the state: a neighbour built later may allow it).
     */
    public static Solution solve(World world, EntityPlayer player, ItemStack stack, IBlockState target, BlockPos pos,
                                 boolean fakeRotation, boolean fakeStand) {
        if (stack.isEmpty()) return null;
        List<EnumFacing> sides = solidSides(world, pos);
        Block placedBlock = blockOf(stack.getItem());
        if (placedBlock == null) return sides.isEmpty() ? null : itemClick(target, pos, sides);
        PrinterLook[] rotations = PrinterLook.candidates(player.rotationYaw);
        PrinterLook[] looks = new PrinterLook[fakeRotation ? rotations.length + 1 : 1];
        if (fakeRotation) System.arraycopy(rotations, 0, looks, 1, rotations.length);
        double px = player.posX, py = player.posY, pz = player.posZ;
        float yaw = player.rotationYaw, pitch = player.rotationPitch;
        try {
            Solution found = anyClick(world, player, placedBlock, stack, target, pos, sides, looks, null);
            if (found != null || !fakeStand) return found;
            // up and down facings of pistons, observers and the like need the player above or below the block
            for (Vec3d stand : FakeStand.candidates(world, player, pos)) {
                player.posX = stand.x; player.posY = stand.y; player.posZ = stand.z;
                found = anyClick(world, player, placedBlock, stack, target, pos, sides, looks, stand);
                player.posX = px; player.posY = py; player.posZ = pz;
                if (found != null) return found;
            }
            return null;
        } finally {
            player.rotationYaw = yaw;
            player.rotationPitch = pitch;
            player.posX = px; player.posY = py; player.posZ = pz;
        }
    }

    /** Doors, beds, signs...: their state comes from the item's own rules; click the block below (or the first solid side). */
    private static Solution itemClick(IBlockState target, BlockPos pos, List<EnumFacing> sides) {
        EnumFacing direction = sides.get(0);
        if (target.getBlock() instanceof BlockWallSign) {
            // a wall sign hangs on the block it is clicked on, facing away from it
            direction = target.getValue(BlockWallSign.FACING).getOpposite();
            if (!sides.contains(direction)) return null;
        }
        return new Solution(pos.offset(direction), direction.getOpposite(), center(pos.offset(direction), direction.getOpposite(), 0.5f), null, null);
    }

    /**
     * With accurate placement the server sets facing, half, axis, delay and mode, so any click that makes the target's
     * block will do: the first solid neighbour, at the player's own rotation and place. Null when the item is no plain
     * block item or no click makes the block (the normal solver is tried then).
     */
    public static Solution solveAccurate(World world, EntityPlayer player, ItemStack stack, IBlockState target, BlockPos pos) {
        if (stack.isEmpty()) return null;
        Block block = blockOf(stack.getItem());
        if (block == null) {
            // not a bed: the player's facing decides where its other half goes, the server could only turn one half
            List<EnumFacing> sides = solidSides(world, pos);
            return sides.isEmpty() || target.getBlock() instanceof BlockBed ? null : itemClick(target, pos, sides);
        }
        for (EnumFacing direction : solidSides(world, pos)) {
            Solution found = makesBlock(world, player, block, stack, target, pos, pos.offset(direction), direction.getOpposite());
            if (found != null) return found;
        }
        return airClicks(world, block, pos) ? makesBlock(world, player, block, stack, target, pos, pos, EnumFacing.DOWN) : null;
    }

    private static Solution makesBlock(World world, EntityPlayer player, Block block, ItemStack stack, IBlockState target, BlockPos pos, BlockPos clicked, EnumFacing side) {
        IBlockState placed = world.mayPlace(block, pos, false, side, null) ? place(world, player, block, stack, pos, side, offset(side.getXOffset()), 0.5f, offset(side.getZOffset())) : null;
        return placed != null && StateMatch.switchedPair(placed.getBlock(), target.getBlock()) ? new Solution(clicked, side, center(clicked, side, 0.5f), null, null) : null;
    }

    /** Whether the free position itself may be clicked: not where the server refuses it, nor for sand and the like with nothing under them (they would fall). */
    private static boolean airClicks(World world, Block block, BlockPos pos) {
        if (Configs.Generic.EASY_PLACE_CLICK_ADJACENT.getBooleanValue()) return false;
        return !(block instanceof BlockFalling && BlockFalling.canFallThrough(world.getBlockState(pos.down())));
    }

    /** The neighbours' faces first, then the position itself. */
    private static Solution anyClick(World world, EntityPlayer player, Block block, ItemStack stack, IBlockState target, BlockPos pos,
                                     List<EnumFacing> sides, PrinterLook[] looks, Vec3d stand) {
        Solution found = search(world, player, block, stack, target, pos, sides, looks, stand, Target.NEIGHBOUR);
        if (found == null && airClicks(world, block, pos)) found = search(world, player, block, stack, target, pos, Arrays.asList(ORDER), looks, stand, Target.ITSELF);
        if (found != null || !RelaxedPlacement.allowed(world, block) || !RelaxedPlacement.free() || !world.getBlockState(pos).getBlock().isReplaceable(world, pos)) return found;
        return search(world, player, block, stack, target, pos, Arrays.asList(ORDER), looks, stand, Target.RELAXED);
    }

    /** Tries the clicks on the neighbours in the given directions, or on those faces of the position itself. */
    private static Solution search(World world, EntityPlayer player, Block block, ItemStack stack, IBlockState target, BlockPos pos,
                                   List<EnumFacing> sides, PrinterLook[] looks, Vec3d stand, Target on) {
        float yaw = player.rotationYaw, pitch = player.rotationPitch;
        for (EnumFacing direction : sides) {
            BlockPos clicked = on == Target.NEIGHBOUR ? pos.offset(direction) : pos;
            EnumFacing side = on == Target.NEIGHBOUR ? direction.getOpposite() : direction;
            float[] heights = side.getAxis() != EnumFacing.Axis.Y ? SIDE_HEIGHTS : new float[] {side == EnumFacing.UP ? 1f : 0f};
            for (float height : heights) {
                for (PrinterLook look : looks) {
                    player.rotationYaw = look == null ? yaw : look.yaw;
                    player.rotationPitch = look == null ? pitch : look.pitch;
                    IBlockState placed = on == Target.RELAXED || world.mayPlace(block, pos, false, side, null)
                        ? place(world, player, block, stack, pos, side, offset(side.getXOffset()), height, offset(side.getZOffset())) : null;
                    player.rotationYaw = yaw;
                    player.rotationPitch = pitch;
                    if (placed != null && StateMatch.placedMatches(placed, target)) {
                        return new Solution(clicked, side, center(clicked, side, height), look, stand, on == Target.RELAXED);
                    }
                }
            }
        }
        return null;
    }

    private static float offset(int direction) {
        return direction == 0 ? 0.5f : direction > 0 ? 1f : 0f;
    }

    private static Vec3d center(BlockPos clicked, EnumFacing side, float height) {
        float y = side.getAxis() == EnumFacing.Axis.Y ? (side == EnumFacing.UP ? 1f : 0f) : height;
        return new Vec3d(clicked.getX() + offset(side.getXOffset()), clicked.getY() + y, clicked.getZ() + offset(side.getZOffset()));
    }

    /** The state the item makes at pos when its neighbour is clicked on the given face (what ItemBlock.onItemUse asks the block), or null. */
    private static IBlockState place(World world, EntityPlayer player, Block block, ItemStack stack, BlockPos pos, EnumFacing side,
                                     float hx, float hy, float hz) {
        int meta = stack.getItem() instanceof ItemBlock ? ((ItemBlock) stack.getItem()).getMetadata(stack.getMetadata()) : 0;
        return facedByPlacer(block, block.getStateForPlacement(world, pos, side, hx, hy, hz, meta, player, EnumHand.MAIN_HAND), pos, player);
    }

    /** Blocks that take their facing in onBlockPlacedBy (after the placement) instead of in getStateForPlacement. */
    private static IBlockState facedByPlacer(Block block, IBlockState state, BlockPos pos, EntityPlayer player) {
        if (block instanceof BlockChest || block instanceof BlockEnderChest || block instanceof BlockFurnace) {
            return state.withProperty(BlockHorizontal.FACING, player.getHorizontalFacing().getOpposite());
        }
        if (block instanceof BlockDispenser) return state.withProperty(BlockDispenser.FACING, EnumFacing.getDirectionFromEntityLiving(pos, player));
        return state;
    }

    /** The block an item places by the ItemBlock / ItemBlockSpecial rules (repeaters, comparators...), or null for any other item. */
    public static Block blockOf(Item item) {
        if (item instanceof ItemBlock) return ((ItemBlock) item).getBlock();
        if (item instanceof ItemBlockSpecial) return ((IMixinItemBlockSpecial) item).getBlock();
        return null;
    }
}
