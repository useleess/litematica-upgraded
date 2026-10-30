package com.useleess.litematicaupgraded.printer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.block.BlockLeaves;
import net.minecraft.block.BlockOldLeaf;
import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;

import org.junit.BeforeClass;
import org.junit.Test;

/** The pure logic of the printer extras: snow layers, tools, scaffold materials, tolerated state differences. */
public class PrinterExtrasTest {
    @BeforeClass
    public static void bootstrap() {
        Bootstrap.register();
    }

    private static IBlockState snow(int layers) {
        return Blocks.SNOW_LAYER.getDefaultState().withProperty(BlockSnow.LAYERS, layers);
    }

    @Test
    public void snowIsBuiltUpToTheSchematicsLayers() {
        assertEquals(2, SnowLayers.missing(snow(1), snow(3)));
        assertEquals(0, SnowLayers.missing(snow(3), snow(3)));
        assertEquals(0, SnowLayers.missing(snow(5), snow(3)));
        assertEquals(0, SnowLayers.missing(Blocks.STONE.getDefaultState(), snow(3)));
        assertEquals(0, SnowLayers.missing(snow(1), Blocks.STONE.getDefaultState()));
    }

    @Test
    public void snowIsPlacedOneLayerFirst() {
        assertEquals(1, (int) SnowLayers.firstLayer(snow(6)).getValue(BlockSnow.LAYERS));
        assertEquals(Blocks.STONE.getDefaultState(), SnowLayers.firstLayer(Blocks.STONE.getDefaultState()));
        assertEquals(0.375, SnowLayers.top(snow(3)), 1e-9);
    }

    @Test
    public void aToolBuildsOnlyItsOwnBlock() {
        IBlockState farmland = Blocks.FARMLAND.getDefaultState();
        IBlockState portal = Blocks.PORTAL.getDefaultState();
        assertTrue(ToolUse.accepts(farmland, new ItemStack(Items.IRON_HOE)));
        assertFalse(ToolUse.accepts(farmland, new ItemStack(Items.FLINT_AND_STEEL)));
        assertTrue(ToolUse.accepts(portal, new ItemStack(Items.FLINT_AND_STEEL)));
        assertFalse(ToolUse.accepts(portal, ItemStack.EMPTY));
    }

    @Test
    public void scaffoldMaterialsAreCheapFullBlocks() {
        assertTrue(Scaffold.MATERIALS.contains(Blocks.COBBLESTONE));
        assertTrue(Scaffold.MATERIALS.contains(Blocks.DIRT));
        for (net.minecraft.block.Block block : Scaffold.MATERIALS) {
            assertTrue(block + " is not a full block", block.getDefaultState().isFullCube());
        }
    }

    @Test
    public void leafDecayFlagsDoNotMakeAnotherState() {
        IBlockState persistent = Blocks.LEAVES.getDefaultState().withProperty(BlockLeaves.DECAYABLE, false).withProperty(BlockLeaves.CHECK_DECAY, false);
        IBlockState decaying = Blocks.LEAVES.getDefaultState().withProperty(BlockLeaves.DECAYABLE, true).withProperty(BlockLeaves.CHECK_DECAY, true);
        assertTrue(StateMatch.built(decaying, persistent));
        IBlockState spruce = persistent.withProperty(BlockOldLeaf.VARIANT, net.minecraft.block.BlockPlanks.EnumType.SPRUCE);
        assertFalse(StateMatch.built(spruce, persistent));
    }
}
