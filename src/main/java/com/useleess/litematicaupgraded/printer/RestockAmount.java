package com.useleess.litematicaupgraded.printer;

import com.google.common.collect.ImmutableList;

import com.useleess.litematicaupgraded.malilib.config.value.BaseOptionConfigValue;

/** How much of an item the shulker restock takes out of a box at a time (#18). */
public final class RestockAmount extends BaseOptionConfigValue {
    public static final RestockAmount FULL_STACK = new RestockAmount("full_stack");
    public static final RestockAmount HALF_STACK = new RestockAmount("half_stack");
    public static final RestockAmount SINGLE_ITEM = new RestockAmount("single_item");

    public static final ImmutableList<RestockAmount> VALUES = ImmutableList.of(FULL_STACK, HALF_STACK, SINGLE_ITEM);

    private RestockAmount(String name) {
        super(name, "litematica_upgraded.label.shulker_restock_amount." + name);
    }

    /** The number of items to take for an item that stacks up to maxStack. */
    public int count(int maxStack) {
        if (this == SINGLE_ITEM) return 1;
        return this == HALF_STACK ? Math.max(1, maxStack / 2) : maxStack;
    }
}
