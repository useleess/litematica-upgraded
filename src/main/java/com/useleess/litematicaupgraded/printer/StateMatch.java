// What counts as "the schematic state" for the printer: the properties a placement click cannot choose are ignored.
// Ideas from Schematica Upgraded (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.printer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

public final class StateMatch {
    /** Properties the world makes (power, neighbours) or a click cycles (delay, mode): never part of a placement. */
    private static final Set<String> RUNTIME = new HashSet<>(Arrays.asList(
        "powered", "power", "lit", "open", "delay", "mode", "triggered", "extended", "north", "south", "east", "west",
        "up", "down", "shape", "snowy", "hinge", "disarmed", "attached", "moisture", "age", "locked", "conditional",
        "in_wall", "contents", "bites", "enabled", "decayable", "check_decay"));

    private StateMatch() {}

    /** The twin blocks that differ only by being lit: an item places the unlit one where the schematic has the lit one. */
    public static boolean switchedPair(Block a, Block b) {
        return a == b || pair(a, b, Blocks.UNPOWERED_REPEATER, Blocks.POWERED_REPEATER)
            || pair(a, b, Blocks.UNPOWERED_COMPARATOR, Blocks.POWERED_COMPARATOR)
            || pair(a, b, Blocks.REDSTONE_TORCH, Blocks.UNLIT_REDSTONE_TORCH)
            || pair(a, b, Blocks.REDSTONE_LAMP, Blocks.LIT_REDSTONE_LAMP)
            || pair(a, b, Blocks.FURNACE, Blocks.LIT_FURNACE);
    }

    private static boolean pair(Block a, Block b, Block x, Block y) {
        return a == x && b == y || a == y && b == x;
    }

    /** Whether the two states have the same block (or lit twin) and equal properties, apart from the ignored names. */
    public static boolean same(IBlockState a, IBlockState b, Set<String> ignored) {
        if (!switchedPair(a.getBlock(), b.getBlock())) return false;
        for (IProperty<?> property : a.getPropertyKeys()) {
            String name = property.getName();
            if (ignored.contains(name) || !b.getPropertyKeys().contains(property)) continue;
            if (!a.getValue(property).equals(b.getValue(property))) return false;
        }
        return true;
    }

    /** Whether a placed (or simulated) state is the schematic state as far as a placement decides it. */
    public static boolean placedMatches(IBlockState placed, IBlockState target) {
        return same(placed, target, RUNTIME);
    }

    /** Whether the world block is built as the schematic wants it, as far as placing and clicking can tell. */
    public static boolean built(IBlockState real, IBlockState target) {
        return same(real, target, RUNTIME);
    }
}
