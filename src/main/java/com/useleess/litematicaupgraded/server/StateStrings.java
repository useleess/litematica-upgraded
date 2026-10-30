// Block states as name[key=value,...] strings, for the messages of the printer support.
package com.useleess.litematicaupgraded.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.google.common.base.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

public final class StateStrings {
    private StateStrings() {}

    /** The state as the game keeps it in a world (metadata only), written name[key=value,...] with sorted keys. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static String toString(IBlockState state) {
        IBlockState stored = Block.getStateById(Block.getStateId(state));
        Map<String, String> properties = new TreeMap<>();
        for (Map.Entry<IProperty<?>, Comparable<?>> entry : stored.getProperties().entrySet()) {
            IProperty property = entry.getKey();
            properties.put(property.getName(), property.getName(entry.getValue()));
        }
        StringBuilder text = new StringBuilder(String.valueOf(Block.REGISTRY.getNameForObject(stored.getBlock())));
        if (!properties.isEmpty()) {
            text.append('[');
            boolean first = true;
            for (Map.Entry<String, String> e : properties.entrySet()) {
                if (!first) text.append(',');
                text.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
            text.append(']');
        }
        return text.toString();
    }

    /** The name part of a state string. */
    static String name(String state) {
        int bracket = state.indexOf('[');
        return bracket < 0 ? state : state.substring(0, bracket);
    }

    /** The properties of a state string, in order. */
    static Map<String, String> properties(String state) {
        Map<String, String> result = new LinkedHashMap<>();
        int bracket = state.indexOf('[');
        if (bracket < 0 || !state.endsWith("]")) return result;
        for (String pair : state.substring(bracket + 1, state.length() - 1).split(",")) {
            int eq = pair.indexOf('=');
            if (eq > 0) result.put(pair.substring(0, eq).trim(), pair.substring(eq + 1).trim());
        }
        return result;
    }

    /** The state of a name[key=value,...] string; null for an unknown block, unknown properties or values keep the default. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static IBlockState parse(String text) {
        if (text == null || text.isEmpty()) return null;
        ResourceLocation location = new ResourceLocation(name(text));
        if (!Block.REGISTRY.containsKey(location)) return null;
        IBlockState state = Block.REGISTRY.getObject(location).getDefaultState();
        for (Map.Entry<String, String> e : properties(text).entrySet()) {
            for (IProperty property : state.getPropertyKeys()) {
                if (!property.getName().equals(e.getKey())) continue;
                Optional parsed = property.parseValue(e.getValue());
                if (parsed.isPresent()) state = state.withProperty(property, (Comparable) parsed.get());
                break;
            }
        }
        return state;
    }
}
