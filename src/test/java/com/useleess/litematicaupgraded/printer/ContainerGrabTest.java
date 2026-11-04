package com.useleess.litematicaupgraded.printer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.inventory.ContainerDispenser;
import net.minecraft.inventory.ContainerFurnace;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.junit.BeforeClass;
import org.junit.Test;

import com.useleess.litematicaupgraded.litematica.materials.MaterialListEntry;
import com.useleess.litematicaupgraded.malilib.config.value.LayerMode;
import com.useleess.litematicaupgraded.malilib.listener.LayerRangeChangeListener;
import com.useleess.litematicaupgraded.malilib.util.position.Direction;
import com.useleess.litematicaupgraded.malilib.util.position.LayerRange;

/** What Grab From Containers wants and in which order; the clicks themselves are the in-game grab/ tests. */
public class ContainerGrabTest {
    @BeforeClass
    public static void bootstrap() {
        Bootstrap.register();
    }

    private static final LayerRangeChangeListener NO_LISTENER = new LayerRangeChangeListener() {
        @Override public void updateAll() {}
        @Override public void updateBetweenX(int minX, int maxX) {}
        @Override public void updateBetweenY(int minY, int maxY) {}
        @Override public void updateBetweenZ(int minZ, int maxZ) {}
    };

    private static MaterialListEntry entry(ItemStack stack, long missing, long available) {
        return new MaterialListEntry(stack, missing, missing, 0, available);
    }

    @Test
    public void carriedItemsAreSubtractedAndTheMostNeededComesFirst() {
        ItemStack stone = new ItemStack(Blocks.STONE), dirt = new ItemStack(Blocks.DIRT), sand = new ItemStack(Blocks.SAND);
        List<ContainerGrab.Need> needs = ContainerGrab.needs(Arrays.asList(entry(stone, 10, 4), entry(dirt, 100, 0), entry(sand, 5, 5)));
        assertEquals(2, needs.size());
        assertSame(Item.getItemFromBlock(Blocks.DIRT), needs.get(0).stack.getItem());
        assertEquals(100, needs.get(0).count);
        assertEquals(6, needs.get(1).count);
    }

    @Test
    public void theMostNeededItemTheContainerHoldsIsNext() {
        ContainerGrab.Need dirt = new ContainerGrab.Need(new ItemStack(Blocks.DIRT), 100), stone = new ContainerGrab.Need(new ItemStack(Blocks.STONE), 6);
        List<ContainerGrab.Need> needs = Arrays.asList(dirt, stone);
        List<ItemStack> slots = Arrays.asList(ItemStack.EMPTY, new ItemStack(Blocks.STONE, 64));
        assertSame(stone, ContainerGrab.next(needs, slots));
        assertEquals(1, ContainerGrab.slotOf(stone, slots));
        stone.count = 0;
        assertNull(ContainerGrab.next(needs, slots));
    }

    @Test
    public void damageValuesAreDifferentItems() {
        ContainerGrab.Need granite = new ContainerGrab.Need(new ItemStack(Blocks.STONE, 1, 1), 5);
        assertEquals(-1, ContainerGrab.slotOf(granite, Collections.singletonList(new ItemStack(Blocks.STONE, 64, 0))));
    }

    @Test
    public void onlyStorageScreensGiveItems() {
        InventoryPlayer player = new InventoryPlayer(null);
        assertTrue(ContainerGrab.storage(new ContainerDispenser(player, new InventoryBasic("dispenser", false, 9))));
        assertFalse(ContainerGrab.storage(new ContainerFurnace(player, new InventoryBasic("furnace", false, 3))));
    }

    @Test
    public void boxesAreCutOnlyAlongTheLayerAxis() {
        LayerRange layers = new LayerRange(NO_LISTENER);
        layers.setLayerMode(LayerMode.SINGLE_LAYER, false);
        layers.setLayerSingle(70);
        assertArrayEquals(new int[] {70, 70}, ContainerGrab.cut(60, 80, Direction.Axis.Y, layers));
        assertArrayEquals(new int[] {60, 80}, ContainerGrab.cut(60, 80, Direction.Axis.X, layers));
        int[] outside = ContainerGrab.cut(0, 10, Direction.Axis.Y, layers);
        assertTrue(outside[0] > outside[1]);
    }
}
