package com.useleess.litematicaupgraded.selftest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import com.useleess.litematicaupgraded.litematica.data.SchematicHolder;
import com.useleess.litematicaupgraded.litematica.schematic.LoadedSchematic;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;
import com.useleess.litematicaupgraded.litematica.world.SchematicWorldHandler;
import com.useleess.litematicaupgraded.malilib.util.position.BlockPos;

/**
 * entities/: a schematic's entities reach the schematic world once each. The chunk-by-chunk copy used to spawn every entity
 * of the schematic in every chunk it touched (18 copies of each iron golem in a 3 x 6 chunk farm), and a re-copied chunk
 * kept its old entities in memory. The test schematic holds 12 armor stands spread over 3 x 3 chunks.
 */
final class EntityTests {
    private static final int STANDS = 12;
    private static SchematicPlacement placement;

    private EntityTests() {
    }

    /** The armor stands the schematic world holds, and how many different positions they stand on. */
    private static int[] stands() {
        World world = SchematicWorldHandler.getSchematicWorld();
        int count = 0;
        Set<String> spots = new HashSet<>();
        for (Entity e : world.loadedEntityList) {
            if (e instanceof EntityArmorStand) {
                count++;
                spots.add(e.posX + " " + e.posY + " " + e.posZ);
            }
        }
        return new int[] {count, spots.size()};
    }

    static void register(Runner.Registry r) {
        PerfTests.Seq once = new PerfTests.Seq();
        long[] builds = new long[1];
        r.waitFor("entities/each-entity-placed-once", 3000, c -> {
            PerfTests.removeAllPlacements();
            PerfTests.resetSettings();
            LoadedSchematic schematic = LoadedSchematic.tryLoadSchematic(PerfTests.copyOut("entities_across_chunks.litematic"))
                    .orElseThrow(() -> new IllegalStateException("cannot load entities_across_chunks.litematic"));
            // the 40 x 40 area starts beside the player: all its chunks are loaded around them
            BlockPos origin = new BlockPos((int) c.mc.player.posX - 20, (int) c.mc.player.posY - 1, (int) c.mc.player.posZ - 20);
            placement = SchematicPlacement.create(schematic, origin, "selftest-entities", true);
            builds[0] = com.useleess.litematicaupgraded.perf.PerfStats.builds();
            PerfTests.manager().addSchematicPlacement(placement, false);
            once.until("the placement to be copied and built", 2000, 20, PerfTests.settled(builds)).once(x -> {
                int[] s = stands();
                x.check(placement.getTouchedChunks().size() >= 4, "the test area touches only " + placement.getTouchedChunks().size() + " chunks");
                x.check(s[0] == STANDS, s[0] + " armor stands in the schematic world, the schematic has " + STANDS);
                x.check(s[1] == STANDS, "the armor stands stand on " + s[1] + " spots, the schematic has " + STANDS);
            });
        }, c -> once.run(c));

        PerfTests.Seq again = new PerfTests.Seq();
        r.waitFor("entities/recopy-and-removal-leave-none-behind", 3000, c -> {
            c.check(placement != null, "the placement of the previous test is missing");
            builds[0] = com.useleess.litematicaupgraded.perf.PerfStats.builds();
            PerfTests.manager().markChunksForRebuild(placement);
            again.until("the chunks to be copied again", 2000, 20, PerfTests.settled(builds)).once(x -> {
                int[] s = stands();
                x.check(s[0] == STANDS, s[0] + " armor stands after copying the chunks again, the schematic has " + STANDS);
                PerfTests.removeAllPlacements();
            }).until("the placement's chunks to be wiped", 2000, 5, x -> stands()[0] == 0);
        }, c -> again.run(c));

        // a schematic saved after a paste with the bug: 3 cows saved 4 times each (+ a single cow, 2 twin armor stands)
        r.add("entities/copied-schematic-warns-in-chat", c -> {
            LoadedSchematic copied = LoadedSchematic.tryLoadSchematic(PerfTests.copyOut("copied_mobs.litematic"))
                    .orElseThrow(() -> new IllegalStateException("cannot load copied_mobs.litematic"));
            SchematicHolder.INSTANCE.addSchematic(copied, false);
            String expected = TextFormatting.getTextWithoutFormattingCodes(I18n.format("litematica_upgraded.message.copied_entities", "copied mobs", 12, 3, 4));
            List<ChatLine> lines = ObfuscationReflectionHelper.getPrivateValue(GuiNewChat.class, c.mc.ingameGUI.getChatGUI(), "field_146252_h");
            boolean shown = false;
            for (ChatLine line : lines) shown |= expected.equals(TextFormatting.getTextWithoutFormattingCodes(line.getChatComponent().getUnformattedText()));
            SchematicHolder.INSTANCE.removeSchematic(copied);
            c.check(shown, "no copied-entities warning in chat for a schematic with 3 cows saved 4 times each");
        });
    }
}
