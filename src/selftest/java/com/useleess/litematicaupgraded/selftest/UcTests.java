package com.useleess.litematicaupgraded.selftest;

import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.BlockStairs;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;

import java.util.function.BooleanSupplier;

import com.useleess.litematicaupgraded.server.ServerFeatures;

/**
 * The uc phase: this client on a real Useleess Carpet server, which answers LU's messages itself (no Forge on the
 * server). Both of its LU rules are on at the start (tools/ingame.ps1); the tests switch them as an op would.
 */
final class UcTests {
    private static final String SHULKER = "litematicaShulkerRestock", ACCURATE = "litematicaAccuratePlacement";
    /** Ticks a rule change may take to reach the client. */
    private static final int TOLD_TICKS = 200;

    private UcTests() {}

    private static boolean allOn() {
        return ServerFeatures.accuratePlacement && ServerFeatures.accurateStates && ServerFeatures.shulkerRestock;
    }

    private static boolean allOff() {
        return !ServerFeatures.accuratePlacement && !ServerFeatures.accurateStates && !ServerFeatures.shulkerRestock;
    }

    private static String state() {
        return "accurate " + ServerFeatures.accuratePlacement + ", full states " + ServerFeatures.accurateStates + ", shulker " + ServerFeatures.shulkerRestock
            + ", air " + ServerFeatures.airPlacement + ", range " + ServerFeatures.range;
    }

    /** Sends the rule change and waits for the client to know it. */
    private static void rule(Runner.Registry r, String id, String rule, boolean on, BooleanSupplier told) {
        r.waitFor(id, TOLD_TICKS, c -> ServerTests.send(c, "carpet " + rule + " " + on), c -> told.getAsBoolean());
    }

    static void register(Runner.Registry r) {
        r.add("uc/load/rules-on-at-join-reach-the-client", c -> {
            c.check(ServerFeatures.received, "no capabilities message arrived from the Useleess Carpet server");
            c.check(ServerFeatures.printerAllowed && allOn(), "the rules are on but the client has: " + state());
            c.check(!ServerFeatures.airPlacement && ServerFeatures.range == 0, "UC offers no air placement or range: " + state());
        });
        ServerTests.registerAccurate(r, "uc/accurate/");
        ServerTests.registerShulker(r, "uc/shulker/");

        rule(r, "uc/live/accurate-rule-off-reaches-the-client", ACCURATE, false,
            () -> !ServerFeatures.accuratePlacement && !ServerFeatures.accurateStates && ServerFeatures.shulkerRestock);
        rule(r, "uc/live/shulker-rule-off-reaches-the-client", SHULKER, false, UcTests::allOff);
        ServerTests.registerClientOnly(r, "uc/off/printer-builds-alone-with-the-rules-off");

        int[] step = {0, 0};
        r.waitFor("uc/off/a-new-join-with-the-rules-off-hears-nothing", 800, c -> {
            step[0] = 0;
            step[1] = 0;
        }, c -> {
            step[1]++;
            switch (step[0]) {
                case 0:
                    ServerTests.leave(c);
                    step[0] = 1;
                    step[1] = 0;
                    return false;
                case 1:
                    if (step[1] < 40) return false;
                    ServerTests.rejoin();
                    step[0] = 2;
                    step[1] = 0;
                    return false;
                case 2:
                    if (c.mc.player == null || c.mc.world == null) return false;
                    step[0] = 3;
                    step[1] = 0;
                    return false;
                default:
                    if (step[1] == 20) ServerTests.send(c, "gamemode creative");
                    if (step[1] < 60) return false;
                    c.check(allOff(), "the rules are off but the client has: " + state());
                    return true;
            }
        });

        rule(r, "uc/live/accurate-rule-on-reaches-the-client", ACCURATE, true,
            () -> ServerFeatures.accuratePlacement && ServerFeatures.accurateStates && !ServerFeatures.shulkerRestock);
        rule(r, "uc/live/shulker-rule-on-reaches-the-client", SHULKER, true, UcTests::allOn);
        Scenario again = ServerTests.accurate("uc/live/accurate-placement-works-after-switching-on");
        again.put(0, 0, 0, Blocks.STONE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.WEST).withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.TOP));
        ServerTests.register(r, again);

        // last: a refused click stops the mid-air tries for the rest of the world
        Scenario relaxed = new Scenario("uc/air/relaxed-placement-fence-gate-and-pumpkin-in-mid-air");
        relaxed.put(0, 2, 0, Blocks.OAK_FENCE_GATE.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.NORTH));
        relaxed.put(3, 2, 0, Blocks.PUMPKIN.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.WEST));
        relaxed.extraCommands.add("carpet relaxedBlockPlacement true");
        ServerTests.register(r, relaxed);
        Scenario refused = new Scenario("uc/air/rule-off-falls-back-to-a-scaffold");
        refused.put(0, 1, 0, Blocks.OAK_FENCE_GATE.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.NORTH));
        refused.extraCommands.add("carpet relaxedBlockPlacement false");
        refused.extraCommands.add("give @s minecraft:dirt 3");
        ServerTests.register(r, refused);
    }
}
