package com.useleess.litematicaupgraded.printer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.math.BlockPos;

import org.junit.BeforeClass;
import org.junit.Test;

/** Which blocks are tried in mid-air, and how the answer to the first click is read; the clicks themselves are the in-game uc/air/ tests. */
public class RelaxedPlacementTest {
    private static final BlockPos POS = new BlockPos(1, 70, 1);

    @BeforeClass
    public static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    public void onlyWhatTheRuleCoversIsTried() {
        assertTrue(RelaxedPlacement.covers(Blocks.OAK_FENCE_GATE));
        assertTrue(RelaxedPlacement.covers(Blocks.ACACIA_FENCE_GATE));
        assertTrue(RelaxedPlacement.covers(Blocks.PUMPKIN));
        assertTrue(RelaxedPlacement.covers(Blocks.LIT_PUMPKIN));
        assertFalse(RelaxedPlacement.covers(Blocks.STONE));
        assertFalse(RelaxedPlacement.covers(Blocks.TORCH));
    }

    @Test
    public void theBlockAppearingEndsTheWaitAndSilenceIsARefusal() {
        RelaxedPlacement.sent(POS, Blocks.OAK_FENCE_GATE);
        assertTrue(RelaxedPlacement.waitingFor(POS));
        assertFalse(RelaxedPlacement.free());
        RelaxedPlacement.seen(Blocks.AIR);
        assertTrue("one tick of air is no answer yet", RelaxedPlacement.waitingFor(POS));
        RelaxedPlacement.seen(Blocks.OAK_FENCE_GATE);
        assertTrue(RelaxedPlacement.free());
        assertFalse(RelaxedPlacement.refused());

        RelaxedPlacement.sent(POS, Blocks.OAK_FENCE_GATE);
        for (int tick = 0; tick < RelaxedPlacement.WAIT_TICKS; tick++) RelaxedPlacement.seen(Blocks.AIR);
        assertTrue(RelaxedPlacement.free());
        assertTrue(RelaxedPlacement.refused());
    }
}
