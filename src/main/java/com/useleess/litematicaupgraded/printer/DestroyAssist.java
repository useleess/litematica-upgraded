// Hold-to-clear destroy assist after the behavior of Buildprint's easy destroy, by HackerRouter, 2026 (Schematica Upgraded, MIT).
package com.useleess.litematicaupgraded.printer;

import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * Destroy assist: pressing the attack button on a block a placement wants gone (an extra block where it has air, a
 * wrong block or a block in the wrong state) takes over from the vanilla attack: while the button stays down the
 * nearest such blocks in reach are mined one after another, with the best tool; other blocks are never touched.
 * The vanilla attack is kept from running by releasing the attack key's state every tick while the assist is active.
 */
public final class DestroyAssist {
    public static final DestroyAssist INSTANCE = new DestroyAssist();
    /** Whether the attack control is physically held; tests replace it. */
    public static final BooleanSupplier PHYSICAL = () -> {
        int code = Minecraft.getMinecraft().gameSettings.keyBindAttack.getKeyCode();
        return code < 0 ? Mouse.isButtonDown(code + 100) : Keyboard.isKeyDown(code);
    };
    public static BooleanSupplier held = PHYSICAL;
    private static boolean active;

    private DestroyAssist() {}

    public static boolean active() { return active; }

    /** Gives control back to the vanilla attack (also resets the test hook). */
    public static void stop() {
        active = false;
        held = PHYSICAL;
        Printer.INSTANCE.stopMining();
    }

    /** The attack was pressed: takes over when the looked-at block is one the placement wants gone. True when it did. */
    public static boolean press(Minecraft mc) {
        if (!PrinterConfigs.DESTROY_ASSIST.getBooleanValue() || mc.currentScreen != null || mc.player == null) return false;
        RayTraceResult hit = mc.objectMouseOver;
        if (hit == null || hit.typeOfHit != RayTraceResult.Type.BLOCK || !Printer.INSTANCE.wantedGone(mc.world, hit.getBlockPos())) return false;
        active = true;
        return true;
    }

    @SubscribeEvent
    public void onMouse(MouseEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!event.isButtonstate() || event.getButton() < 0 || event.getButton() - 100 != mc.gameSettings.keyBindAttack.getKeyCode()) return;
        if (press(mc)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!active) return;
        if (event.phase == TickEvent.Phase.START) {
            // the vanilla attack mines whatever is looked at while its key state says "down"
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        } else {
            tick(mc);
        }
    }

    /** Mines on while the button is held; the next block is the looked-at one, else the nearest one in reach. */
    static void tick(Minecraft mc) {
        EntityPlayerSP player = mc.player;
        World world = mc.world;
        Printer printer = Printer.INSTANCE;
        if (player == null || world == null || mc.currentScreen != null || !held.getAsBoolean() || !PrinterConfigs.DESTROY_ASSIST.getBooleanValue()) {
            stop();
            return;
        }
        double eyeX = player.posX, eyeY = player.posY + player.getEyeHeight(), eyeZ = player.posZ;
        double range = mc.playerController.getBlockReachDistance();
        if (printer.continueMining(world, eyeX, eyeY, eyeZ, range)) return;
        RayTraceResult hit = mc.objectMouseOver;
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK && printer.wantedGone(world, hit.getBlockPos())) {
            printer.beginMining(world, hit.getBlockPos(), false);
            return;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        int r = (int) Math.ceil(range), bx = MathHelper.floor(eyeX), by = MathHelper.floor(eyeY), bz = MathHelper.floor(eyeZ);
        for (int x = bx - r; x <= bx + r; x++) for (int y = Math.max(0, by - r); y <= Math.min(255, by + r); y++) for (int z = bz - r; z <= bz + r; z++) {
            double dx = x + 0.5 - eyeX, dy = y + 0.5 - eyeY, dz = z + 0.5 - eyeZ, distance = dx * dx + dy * dy + dz * dz;
            BlockPos pos = new BlockPos(x, y, z);
            if (distance > range * range || distance >= bestDistance || world.isAirBlock(pos) || !printer.wantedGone(world, pos)) continue;
            best = pos;
            bestDistance = distance;
        }
        if (best != null) printer.beginMining(world, best, false);
    }
}
