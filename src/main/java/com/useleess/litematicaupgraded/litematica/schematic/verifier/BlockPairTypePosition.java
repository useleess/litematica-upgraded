package com.useleess.litematicaupgraded.litematica.schematic.verifier;

import com.useleess.litematicaupgraded.malilib.util.position.BlockPos;
import com.useleess.litematicaupgraded.malilib.util.position.PositionUtils;

public class BlockPairTypePosition
{
    public final VerifierResultType type;
    public final BlockStatePair pair;
    public final long posLong;

    public BlockPairTypePosition(BlockStatePair pair, long posLong)
    {
        this.pair = pair;
        this.type = pair.type;
        this.posLong = posLong;
    }

    public BlockPos getBlockPos()
    {
        return BlockPos.fromPacked(this.posLong);
    }

    public static BlockPairTypePosition of(BlockStatePair pair, long chunkPosLong, int chunkRelativePosition)
    {
        long posLong = PositionUtils.getPackedAbsolutePosition(chunkPosLong, chunkRelativePosition);
        return new BlockPairTypePosition(pair, posLong);
    }
}
