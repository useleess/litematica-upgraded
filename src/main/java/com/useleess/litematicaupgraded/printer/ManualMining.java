// Breaking blocks by hand while the printer runs, after Schematica Upgraded's ManualMining (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.printer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.useleess.litematicaupgraded.litematica.world.SchematicWorldHandler;

/**
 * The server keeps one digging block per player, so the printer's slot swaps would cancel a block the player mines by
 * hand: while the player mines, the printer pauses. A block the player breaks by hand where the placement wanted exactly
 * that block is not placed back until the printer is turned off and on again; a wrong or extra block broken by hand is
 * replaced as usual.
 */
public final class ManualMining {
    public static final ManualMining INSTANCE = new ManualMining();
    /** Ticks a clicked block is watched after the player stopped looking at it while mining. */
    private static final int WATCH_TICKS = 20;

    private static final Map<BlockPos, Watch> watched = new HashMap<>();
    private static final Set<BlockPos> broken = new HashSet<>();
    private static boolean mining;
    private static World world;

    private static final class Watch {
        final IBlockState state;
        final boolean built;
        int idle;

        Watch(IBlockState state, boolean built) { this.state = state; this.built = built; }
    }

    private ManualMining() {}

    /** Whether the player is mining a block by hand (the printer pauses meanwhile). */
    public static boolean active() { return mining; }

    /** Whether the player broke the placement's block at this position by hand. */
    public static boolean skipped(BlockPos pos) { return !broken.isEmpty() && broken.contains(pos); }

    public static void forget() { broken.clear(); }

    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getWorld().isRemote) return;
        World schematic = SchematicWorldHandler.getSchematicWorld();
        if (schematic == null) return;
        BlockPos pos = event.getPos();
        IBlockState wanted = schematic.getBlockState(pos);
        IBlockState real = event.getWorld().getBlockState(pos);
        watched.put(pos.toImmutable(), new Watch(real, !wanted.getBlock().isAir(wanted, schematic, pos) && StateMatch.built(real, wanted)));
    }

    /** Called every client tick before the printer: notices hand mining and the blocks it broke. */
    public static void tick(Minecraft mc) {
        if (mc.world != world) {
            watched.clear();
            broken.clear();
            world = mc.world;
        }
        if (world == null || mc.player == null || mc.playerController == null) {
            mining = false;
            return;
        }
        // a watched block stays watched for as long as the player mines
        RayTraceResult hit = mc.objectMouseOver;
        BlockPos target = hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK ? hit.getBlockPos() : null;
        mining = mc.currentScreen == null
            && (mc.playerController.getIsHittingBlock() || mc.gameSettings.keyBindAttack.isKeyDown() && target != null);
        for (Iterator<Map.Entry<BlockPos, Watch>> it = watched.entrySet().iterator(); it.hasNext();) {
            Map.Entry<BlockPos, Watch> entry = it.next();
            BlockPos pos = entry.getKey();
            Watch watch = entry.getValue();
            IBlockState now = world.getBlockState(pos);
            if (now != watch.state) {
                if (watch.built && now.getBlock().isAir(now, world, pos)) broken.add(pos);
                it.remove();
            } else if (mining) {
                watch.idle = 0;
            } else if (++watch.idle > WATCH_TICKS) {
                it.remove();
            }
        }
    }
}
