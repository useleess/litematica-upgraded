package com.useleess.litematicaupgraded.printer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;

import org.junit.BeforeClass;
import org.junit.Test;

/** Which containers the printer fills, which of their slots, and the slot order of a double chest. */
public class ContainerFillerTest {
    @BeforeClass
    public static void bootstrap() {
        Bootstrap.register();
    }

    @Test
    public void everyContainerKindIsFilled() {
        Block[] filled = {Blocks.HOPPER, Blocks.DROPPER, Blocks.DISPENSER, Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.FURNACE,
            Blocks.LIT_FURNACE, Blocks.BREWING_STAND, Blocks.PURPLE_SHULKER_BOX, Blocks.WHITE_SHULKER_BOX};
        for (Block block : filled) assertTrue(block + " is filled", ContainerFiller.fillable(block));
        assertFalse(ContainerFiller.fillable(Blocks.ENDER_CHEST));
        assertFalse(ContainerFiller.fillable(Blocks.STONE));
        assertFalse(ContainerFiller.fillable(Blocks.JUKEBOX));
    }

    @Test
    public void aFurnaceOutputSlotIsSkipped() {
        assertTrue(ContainerFiller.playerFillable(Blocks.FURNACE, 0));
        assertTrue(ContainerFiller.playerFillable(Blocks.FURNACE, 1));
        assertFalse(ContainerFiller.playerFillable(Blocks.FURNACE, 2));
        assertFalse(ContainerFiller.playerFillable(Blocks.LIT_FURNACE, 2));
        assertTrue(ContainerFiller.playerFillable(Blocks.CHEST, 2));
        assertTrue(ContainerFiller.playerFillable(Blocks.BREWING_STAND, 4));
    }

    @Test
    public void theWestOrNorthHalfOfADoubleChestComesFirst() {
        ItemStack[] own = {new ItemStack(Items.APPLE), ItemStack.EMPTY};
        ItemStack[] partner = {ItemStack.EMPTY, new ItemStack(Items.ARROW)};
        ItemStack[] west = ContainerFiller.joined(own, partner, EnumFacing.WEST);
        assertEquals(4, west.length);
        assertTrue(west[1].getItem() == Items.ARROW && west[2].getItem() == Items.APPLE);
        assertTrue(ContainerFiller.joined(own, partner, EnumFacing.NORTH)[1].getItem() == Items.ARROW);
        ItemStack[] east = ContainerFiller.joined(own, partner, EnumFacing.EAST);
        assertTrue(east[0].getItem() == Items.APPLE && east[3].getItem() == Items.ARROW);
        assertTrue(ContainerFiller.joined(own, partner, EnumFacing.SOUTH)[0].getItem() == Items.APPLE);
    }
}
