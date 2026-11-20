package com.useleess.litematicaupgraded.materials;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;

import com.google.common.base.Optional;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

/**
 * The rules of replacing blocks: the material list's Replace (block type swapped for another) and Schematic Edit mode's
 * replace (#14: the new block keeps how the old one was turned and set, never its material).
 */
public final class ReplaceRules {
    /** Properties a builder chooses for a block: how it is turned and set, never what it is made of (variant, colour). */
    private static final Set<String> SETUP = new HashSet<>(Arrays.asList(
            "facing", "half", "axis", "shape", "hinge", "open", "rotation", "in_wall", "delay", "mode"));

    private ReplaceRules() {}

    /**
     * The new block's default state with every property both blocks share copied over (same name, a value the new block
     * allows: facing, half, axis, shape...); the old block's other properties are dropped.
     */
    public static IBlockState withSharedProperties(IBlockState from, Block to) {
        IBlockState result = to.getDefaultState();
        for (IProperty<?> property : result.getPropertyKeys()) {
            IProperty<?> old = property(from, property.getName());
            if (old != null) result = copy(result, property, valueName(from, old));
        }
        return result;
    }

    /** The new state with how the old block was turned and set copied over (facing, open, half, hinge ...), not its material. */
    public static IBlockState keepingSetup(IBlockState from, IBlockState to) {
        if (from.getBlock() == Blocks.AIR) return to;
        IBlockState result = to;
        for (IProperty<?> property : to.getPropertyKeys()) {
            IProperty<?> old = property(from, property.getName());
            if (old != null && SETUP.contains(property.getName())) result = copy(result, property, valueName(from, old));
        }
        return result;
    }

    /** +1 for the lower half of a two blocks tall block (door, tall plant), -1 for its upper half, 0 for other blocks. */
    public static int otherHalfDy(IBlockState state) {
        IProperty<?> half = twoBlockHalf(state);
        if (half == null) return 0;
        return valueName(state, half).equals("lower") ? 1 : -1;
    }

    /** The same block's other half (upper for lower and back), or null when it is not a two blocks tall block. */
    @Nullable
    public static IBlockState otherHalf(IBlockState state) {
        IProperty<?> half = twoBlockHalf(state);
        if (half == null) return null;
        return copy(state, half, valueName(state, half).equals("lower") ? "upper" : "lower");
    }

    @Nullable
    private static IProperty<?> twoBlockHalf(IBlockState state) {
        IProperty<?> half = property(state, "half");
        if (half == null || half.getAllowedValues().size() != 2) return null;
        return copy(state, half, "upper") != state || copy(state, half, "lower") != state ? half : null;
    }

    @Nullable
    private static IProperty<?> property(IBlockState state, String name) {
        for (IProperty<?> property : state.getPropertyKeys()) {
            if (property.getName().equals(name)) return property;
        }
        return null;
    }

    private static <T extends Comparable<T>> String valueName(IBlockState state, IProperty<T> property) {
        return property.getName(state.getValue(property));
    }

    private static <T extends Comparable<T>> IBlockState copy(IBlockState state, IProperty<T> property, String valueName) {
        Optional<T> value = property.parseValue(valueName);
        return value.isPresent() ? state.withProperty(property, value.get()) : state;
    }
}
