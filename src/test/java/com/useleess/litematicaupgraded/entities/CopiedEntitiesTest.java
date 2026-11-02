package com.useleess.litematicaupgraded.entities;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import com.useleess.litematicaupgraded.malilib.util.data.tag.CompoundData;

public class CopiedEntitiesTest
{
    private static long ids;

    private static CompoundData entity(String id, boolean mob, double x, double z, float health)
    {
        CompoundData tag = new CompoundData().putString("id", id).putDouble("X", x).putDouble("Z", z).putFloat("Health", health)
                .putLong("UUIDMost", ++ids).putLong("UUIDLeast", ++ids);
        return mob ? tag.putBoolean("CanPickUpLoot", false) : tag;
    }

    private static void add(List<CompoundData> list, int copies, String id, boolean mob, double x, double z, float health)
    {
        for (int i = 0; i < copies; i++)
        {
            list.add(entity(id, mob, x, z, health));
        }
    }

    @Test
    public void golemsSavedEighteenTimesEachAreFound()
    {
        List<CompoundData> list = new ArrayList<>();
        for (int spot = 0; spot < 30; spot++) add(list, 18, "minecraft:villager_golem", true, spot + 0.3, 4.7, 100f);
        assertArrayEquals(new int[] {30, 18}, CopiedEntities.find(list));
    }

    @Test
    public void entitiesAddedAfterThePasteDoNotHideTheCopies()
    {
        // the slime farm that showed the bug: golems x18, its minecarts and items single
        List<CompoundData> list = new ArrayList<>();
        for (int spot = 0; spot < 30; spot++) add(list, 18, "minecraft:villager_golem", true, spot, 1, 100f);
        for (int cart = 0; cart < 6; cart++) add(list, 1, "minecraft:hopper_minecart", false, cart, 9, 0f);
        add(list, 1, "minecraft:villager_golem", true, 50, 50, 87f);
        assertArrayEquals(new int[] {30, 18}, CopiedEntities.find(list));
    }

    @Test
    public void aCrowdedTradingHallIsNotTakenForCopies()
    {
        // 30 villagers in one block: never perfect twins (pushed apart, own health)
        List<CompoundData> list = new ArrayList<>();
        for (int v = 0; v < 30; v++) add(list, 1, "minecraft:villager", true, 10.5 + v * 0.001, 10.5, 20f - v * 0.1f);
        assertNull(CopiedEntities.find(list));
    }

    @Test
    public void twinArmorStandsOrMinecartsAreNoEvidence()
    {
        List<CompoundData> list = new ArrayList<>();
        add(list, 18, "minecraft:armor_stand", false, 3.5, 3.5, 20f);
        add(list, 6, "minecraft:minecart", false, 7.5, 7.5, 0f);
        assertNull(CopiedEntities.find(list));
    }

    @Test
    public void mobsTwinnedADifferentNumberOfTimesAreNotTrusted()
    {
        List<CompoundData> list = new ArrayList<>();
        add(list, 18, "minecraft:villager_golem", true, 1, 1, 100f);
        add(list, 5, "minecraft:villager_golem", true, 2, 2, 100f);
        assertNull(CopiedEntities.find(list));
    }
}
