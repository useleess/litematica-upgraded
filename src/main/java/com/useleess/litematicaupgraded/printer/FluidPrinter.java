// Fluid placement for the printer: buckets aimed past a neighbor's face, by HackerRouter, 2026 (Schematica Upgraded, MIT).
package com.useleess.litematicaupgraded.printer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.item.ItemBucket;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.UniversalBucket;

/**
 * A fluid source of the schematic is placed with a bucket (vanilla or Forge's universal bucket) holding it: the player
 * turns toward the face of a neighbor next to the target, as the bucket places its fluid in front of the block it is
 * aimed at, and uses the bucket.
 */
public final class FluidPrinter {
    private static final int BUCKET = Fluid.BUCKET_VOLUME;

    private FluidPrinter() {}

    public static boolean isFluid(Block block) { return block instanceof BlockLiquid || block instanceof IFluidBlock; }

    static Fluid fluid(Block block) {
        return isFluid(block) ? FluidRegistry.lookupFluidForBlock(block) : null;
    }

    /** Whether the block at the position is a full source block of its fluid. */
    static boolean source(World world, BlockPos pos) {
        IBlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof IFluidBlock) return ((IFluidBlock) state.getBlock()).canDrain(world, pos);
        return state.getBlock() instanceof BlockLiquid && state.getValue(BlockLiquid.LEVEL) == 0;
    }

    /** Whether the schematic wants a source here (the printer only places sources; flowing water follows from them). */
    static boolean wantsSource(World schematic, BlockPos pos) {
        return fluid(schematic.getBlockState(pos).getBlock()) != null && source(schematic, pos);
    }

    /** Whether the world already has the schematic's fluid here as a source: flowing and still water are the same fluid. */
    static boolean matches(World schematic, World world, BlockPos pos) {
        Fluid expected = fluid(schematic.getBlockState(pos).getBlock());
        return expected != null && expected == fluid(world.getBlockState(pos).getBlock()) && source(world, pos);
    }

    private static boolean isContainer(ItemStack stack, Fluid fluid) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemBucket || stack.getItem() instanceof UniversalBucket)) return false;
        FluidStack contained = FluidUtil.getFluidContained(stack);
        return contained != null && contained.amount >= BUCKET && contained.getFluid() == fluid;
    }

    private static List<ItemStack> findContainers(EntityPlayerSP player, Fluid fluid) {
        List<ItemStack> containers = new ArrayList<>();
        for (ItemStack stack : player.inventory.mainInventory) {
            if (isContainer(stack, fluid)) containers.add(stack.copy());
        }
        return containers;
    }

    /** Whether the player carries a bucket of the fluid. */
    static boolean hasBucket(EntityPlayerSP player, Fluid fluid) {
        return fluid != null && !findContainers(player, fluid).isEmpty();
    }

    /** Places the fluid with a container the selector puts into the main hand; false when no aim or container works. */
    static boolean place(Minecraft minecraft, Fluid fluid, BlockPos pos, Predicate<ItemStack> selectContainer) {
        if (fluid == null || !fluid.canBePlacedInWorld()) return false;
        EntityPlayerSP player = minecraft.player;
        World world = minecraft.world;
        if (fluid == FluidRegistry.WATER && world.provider.doesWaterVaporize()) return false;
        List<ItemStack> containers = findContainers(player, fluid);
        if (containers.isEmpty()) return false;
        Vec3d start = player.getPositionEyes(1f);
        double reach = Math.min(5.0, minecraft.playerController.getBlockReachDistance());
        float yaw = player.rotationYaw, pitch = player.rotationPitch;
        boolean sentLook = false;
        try {
            for (EnumFacing neighbor : EnumFacing.values()) {
                double hitX = pos.getX() + 0.5 + neighbor.getXOffset() * 0.55;
                double hitY = pos.getY() + 0.5 + neighbor.getYOffset() * 0.55;
                double hitZ = pos.getZ() + 0.5 + neighbor.getZOffset() * 0.55;
                double dx = hitX - start.x, dy = hitY - start.y, dz = hitZ - start.z;
                float aimYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
                float aimPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)));
                player.rotationYaw = aimYaw;
                player.rotationPitch = aimPitch;
                Vec3d look = player.getLook(1.0f);
                RayTraceResult hit = world.rayTraceBlocks(start, new Vec3d(start.x + look.x * reach, start.y + look.y * reach, start.z + look.z * reach), false, true, false);
                if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK) continue;
                if (!hit.getBlockPos().offset(hit.sideHit).equals(pos)) continue;
                IBlockState clicked = world.getBlockState(hit.getBlockPos());
                if (isFluid(clicked.getBlock()) || clicked.getBlock().isReplaceable(world, hit.getBlockPos())) continue;
                for (ItemStack container : containers) {
                    if (!selectContainer.test(container)) continue;
                    player.connection.sendPacket(new CPacketPlayer.Rotation(aimYaw, aimPitch, player.onGround));
                    sentLook = true;
                    PrinterRange.use(player, EnumHand.MAIN_HAND);
                    player.swingArm(EnumHand.MAIN_HAND);
                    return true;
                }
                return false;
            }
            return false;
        } finally {
            player.rotationYaw = yaw;
            player.rotationPitch = pitch;
            if (sentLook) player.connection.sendPacket(new CPacketPlayer.Rotation(yaw, pitch, player.onGround));
        }
    }
}
