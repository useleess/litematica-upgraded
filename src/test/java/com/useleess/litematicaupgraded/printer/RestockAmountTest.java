package com.useleess.litematicaupgraded.printer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** #18: the shulker restock takes a full stack, half a stack or one item. */
public class RestockAmountTest {
    @Test public void amountsPerMode() {
        assertEquals(64, RestockAmount.FULL_STACK.count(64));
        assertEquals(32, RestockAmount.HALF_STACK.count(64));
        assertEquals(1, RestockAmount.SINGLE_ITEM.count(64));
        assertEquals(8, RestockAmount.HALF_STACK.count(16));
        assertEquals(1, RestockAmount.HALF_STACK.count(1));
        assertEquals(1, RestockAmount.FULL_STACK.count(1));
    }

    @Test public void fullStackStaysTheDefault() {
        assertEquals(RestockAmount.FULL_STACK, PrinterConfigs.SHULKER_RESTOCK_AMOUNT.getDefaultValue());
    }
}
