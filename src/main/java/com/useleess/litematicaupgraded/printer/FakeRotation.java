// Easy Place on servers without a mod: the fake rotation of Schematica Upgraded's EasyPlace (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.printer;

import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import com.useleess.litematicaupgraded.litematica.world.SchematicWorldHandler;
import com.useleess.litematicaupgraded.server.ServerFeatures;

/**
 * Litematica's Easy Place tells the server the facing in the click's hit position, which only a server with the matching
 * mod understands. Here the click is replaced, when the block is one the printer's solver knows: with accurate placement
 * on the server any click that makes the block does and the server sets the schematic state; else the rotation (and
 * position) the schematic state needs are reported to the server before the click, as the printer does.
 */
public final class FakeRotation {
    private static boolean busy;
    /** Where the last click asked the server for the whole schematic state (no state clicks are needed there). */
    private static BlockPos announced;

    private FakeRotation() {}

    /**
     * Called for a click Easy Place makes. The result of the replaced click, or null to let Litematica's own click go on.
     */
    public static EnumActionResult click(EntityPlayerSP player, WorldClient world, BlockPos pos, EnumFacing side, EnumHand hand) {
        if (busy || !PrinterConfigs.EASY_PLACE_FAKE_ROTATION.getBooleanValue()) return null;
        World schematic = SchematicWorldHandler.getSchematicWorld();
        if (schematic == null) return null;
        BlockPos target = world.getBlockState(pos).getBlock().isReplaceable(world, pos) ? pos : pos.offset(side);
        IBlockState wanted = schematic.getBlockState(target);
        ItemStack held = player.getHeldItem(hand);
        // slabs have their own click logic in Litematica (halves, double slabs)
        if (wanted.getBlock() instanceof BlockSlab || wanted.getBlock().isAir(wanted, schematic, target) || PlacementSolver.blockOf(held.getItem()) == null) return null;
        PlacementSolver.Solution solution = ServerAssist.accurate(wanted.getBlock())
            ? PlacementSolver.solveAccurate(world, player, held, wanted, target) : null;
        if (solution != null) {
            ServerAssist.announce(target, wanted);
            announced = ServerFeatures.accurateStates ? target.toImmutable() : null;
        } else {
            announced = null;
            solution = PlacementSolver.solve(world, player, held, wanted, target, true, PrinterConfigs.FAKE_POSITION.getBooleanValue());
        }
        if (solution == null) return null;
        busy = true;
        try {
            Minecraft mc = Minecraft.getMinecraft();
            return Printer.click(mc, player, world, solution, hand, true) ? EnumActionResult.SUCCESS : EnumActionResult.FAIL;
        } finally {
            busy = false;
        }
    }

    /**
     * After Easy Place placed a block, or when it aims at a built block in the wrong open / switch / delay / mode state:
     * the clicks that give the block the schematic's state, made at once. Whether any click was needed.
     */
    public static boolean fixState(EntityPlayerSP player, WorldClient world, BlockPos pos) {
        World schematic = SchematicWorldHandler.getSchematicWorld();
        if (pos.equals(announced)) {
            // the server sets the state itself; the client shows the vanilla one until its update arrives
            announced = null;
            return false;
        }
        if (busy || schematic == null) return false;
        IBlockState target = schematic.getBlockState(pos);
        int clicks = StateClicks.clicks(world.getBlockState(pos), target);
        if (clicks <= 0) return false;
        busy = true;
        try {
            Printer.clickState(Minecraft.getMinecraft(), player, world, pos, target, clicks);
            return true;
        } finally {
            busy = false;
        }
    }
}
