package com.useleess.litematicaupgraded.materials;

import com.google.common.base.Optional;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;

/** The rules of the material list's Replace: which state a block becomes when its block type is swapped for another. */
public final class ReplaceRules {
    private ReplaceRules() {}

    /**
     * The new block's default state with every property both blocks share copied over (same name, a value the new block
     * allows: facing, half, axis, shape...); the old block's other properties are dropped.
     */
    public static IBlockState withSharedProperties(IBlockState from, Block to) {
        IBlockState result = to.getDefaultState();
        for (IProperty<?> property : result.getPropertyKeys()) {
            for (IProperty<?> old : from.getPropertyKeys()) {
                if (old.getName().equals(property.getName())) result = copy(result, property, valueName(from, old));
            }
        }
        return result;
    }

    private static <T extends Comparable<T>> String valueName(IBlockState state, IProperty<T> property) {
        return property.getName(state.getValue(property));
    }

    private static <T extends Comparable<T>> IBlockState copy(IBlockState state, IProperty<T> property, String valueName) {
        Optional<T> value = property.parseValue(valueName);
        return value.isPresent() ? state.withProperty(property, value.get()) : state;
    }
}
