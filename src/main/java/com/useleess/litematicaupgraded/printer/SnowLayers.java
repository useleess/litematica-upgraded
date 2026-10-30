// Snow layers built up one click at a time, after the behavior of sakura-ryoko's litematica-printer2, by HackerRouter, 2026 (Schematica Upgraded, MIT).
package com.useleess.litematicaupgraded.printer;

import net.minecraft.block.BlockSnow;
import net.minecraft.block.state.IBlockState;

/**
 * A snow layer item places one layer; using it on the top of a snow block adds one more (ItemSnow), up to eight. The
 * printer places the first layer like any block and then clicks the top of the snow until it has the schematic's layers.
 */
public final class SnowLayers {
    private SnowLayers() {}

    /** The layers to add to the world's snow for the schematic's: 0 when it is no snow, or has as many or more. */
    public static int missing(IBlockState real, IBlockState target) {
        if (!(target.getBlock() instanceof BlockSnow) || real.getBlock() != target.getBlock()) return 0;
        return Math.max(0, target.getValue(BlockSnow.LAYERS) - real.getValue(BlockSnow.LAYERS));
    }

    /** What one snow item places on its own: the first layer (the rest are added by clicking the snow). */
    public static IBlockState firstLayer(IBlockState target) {
        return target.getBlock() instanceof BlockSnow ? target.withProperty(BlockSnow.LAYERS, 1) : target;
    }

    /** The height of the top of the snow, for the click that adds a layer. */
    public static double top(IBlockState real) {
        return real.getBlock() instanceof BlockSnow ? real.getValue(BlockSnow.LAYERS) / 8.0 : 1;
    }
}
