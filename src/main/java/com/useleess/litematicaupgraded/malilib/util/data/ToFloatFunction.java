package com.useleess.litematicaupgraded.malilib.util.data;

@FunctionalInterface
public interface ToFloatFunction<T>
{
    float applyAsFloat(T value);
}
