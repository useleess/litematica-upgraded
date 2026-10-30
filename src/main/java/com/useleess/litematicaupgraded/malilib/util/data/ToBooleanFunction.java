package com.useleess.litematicaupgraded.malilib.util.data;

@FunctionalInterface
public interface ToBooleanFunction<T>
{
    boolean applyAsBoolean(T value);
}
