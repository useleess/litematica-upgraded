package com.useleess.litematicaupgraded.printer;

import net.minecraft.block.Block;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockPumpkin;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.useleess.litematicaupgraded.litematica.config.Configs;

/**
 * Carpet's rule relaxedBlockPlacement lets fence gates and pumpkins stand in mid-air, but the game's own client will not
 * send that click (Carpet Client changes that), so it is sent directly. No client can ask for the rule: the first click
 * is the question. When the block does not appear, the server refused and the printer stops trying in this world.
 */
final class RelaxedPlacement {
    /** Ticks the server gets to answer a click. */
    static final int WAIT_TICKS = 40;

    private static World world;
    private static boolean refused;
    private static BlockPos waiting;
    private static Block block;
    private static int left;

    private RelaxedPlacement() {}

    static boolean covers(Block block) {
        return block instanceof BlockFenceGate || block instanceof BlockPumpkin;
    }

    /** Whether this block may be tried in mid-air here. */
    static boolean allowed(World current, Block block) {
        if (current != world) {
            world = current;
            refused = false;
            waiting = null;
        }
        return covers(block) && !refused && !Configs.Generic.EASY_PLACE_CLICK_ADJACENT.getBooleanValue();
    }

    /** Whether the server answered a click with silence in this world. */
    static boolean refused() {
        return refused;
    }

    /** Whether a click can go out now: one at a time, so that a refusal is seen before the next. */
    static boolean free() {
        return waiting == null;
    }

    static boolean waitingFor(BlockPos pos) {
        return pos.equals(waiting);
    }

    static EnumActionResult send(EntityPlayerSP player, PlacementSolver.Solution solution, EnumHand hand) {
        player.connection.sendPacket(new CPacketHeldItemChange(player.inventory.currentItem));
        BlockPos pos = solution.clicked;
        player.connection.sendPacket(new CPacketPlayerTryUseItemOnBlock(pos, solution.side, hand,
            (float) (solution.hit.x - pos.getX()), (float) (solution.hit.y - pos.getY()), (float) (solution.hit.z - pos.getZ())));
        sent(pos.toImmutable(), PlacementSolver.blockOf(player.getHeldItem(hand).getItem()));
        return EnumActionResult.SUCCESS;
    }

    static void sent(BlockPos pos, Block placed) {
        waiting = pos;
        block = placed;
        left = WAIT_TICKS;
    }

    /** Watches for the server's answer to the click. */
    static void tick(World current) {
        if (waiting != null && current != null) seen(current.getBlockState(waiting).getBlock());
    }

    /** What stands at the position of the open click: the block ends the wait, the time running out means the server refused. */
    static void seen(Block found) {
        if (found == block) {
            waiting = null;
        } else if (--left <= 0) {
            refused = true;
            waiting = null;
        }
    }
}
