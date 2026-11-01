package com.useleess.litematicaupgraded.earlyfix;

import java.io.File;
import java.util.List;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

/**
 * Registers {@link MasaMalilibForgeFix}; started by LiteLoader from litemod.json (tweakClass) while the game is set up -
 * early enough, unlike litemod.json's classTransformerClasses, which come after Mixin has read masa's mixin classes.
 * The game's launcher loads a tweaker's whole package outside the game, so this package holds only the early fix: with
 * any other class here (2026-10-31: InterfaceAccess in compat/) that class saw the game's classes without our mixins.
 */
public final class MasaMalilibForgeFixTweaker implements ITweaker {
    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        classLoader.registerTransformer(MasaMalilibForgeFix.class.getName());
    }

    @Override
    public String getLaunchTarget() {
        return "net.minecraft.client.main.Main";
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }
}
