package com.useleess.litematicaupgraded.litematica.schematic.container;

import com.useleess.litematicaupgraded.malilib.util.position.Vec3i;

public interface BlockContainerFactory
{
    BlockContainer create(Vec3i containerSize);
}
