package com.useleess.litematicaupgraded.compat;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.Config;

/**
 * OptiFine rewrites parts of the game's renderer, so a few hooks of the Forge port of malilib (EntityRenderer: the
 * call that draws the open screen) find nothing to hook into. Every hook of that config is "required", so the game
 * would stop at start. With OptiFine installed (only then) that config's hooks are made optional: a hook that finds
 * its spot works as before, one that does not is skipped and the game runs. Without OptiFine nothing changes.
 * The plugin of mixins.litematica_upgraded.json, which is loaded after the other two configs of the jar.
 */
public final class OptifineMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("Litematica Upgraded");
    /** The configs whose hooks OptiFine is known to move. */
    private static final String[] RELAXED = {"mixins.malilib.json"};

    public static boolean optifinePresent() {
        ClassLoader loader = OptifineMixinPlugin.class.getClassLoader();
        return loader.getResource("optifine/OptiFineForgeTweaker.class") != null || loader.getResource("net/optifine/reflect/Reflector.class") != null;
    }

    @Override
    public void onLoad(String mixinPackage) {
        if (optifinePresent() == false) return;
        for (String name : RELAXED) {
            try {
                Config config = Config.getAllConfigs().get(name);
                if (config == null) continue;
                IMixinConfig mixinConfig = config.getConfig();
                Field options = mixinConfig.getClass().getDeclaredField("injectorOptions");
                options.setAccessible(true);
                Object injectorOptions = options.get(mixinConfig);
                Field require = injectorOptions.getClass().getDeclaredField("defaultRequireValue");
                require.setAccessible(true);
                require.setInt(injectorOptions, 0);
                LOGGER.info("OptiFine found: the hooks of {} are optional (one OptiFine moved is skipped instead of stopping the game)", name);
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.warn("OptiFine found, but the hooks of {} could not be made optional", name, e);
            }
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
