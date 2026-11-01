package com.useleess.litematicaupgraded.compat;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfig;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Makes a config's hooks optional when another mod is known to take their spots; every hook is "required", so one that
 * finds nothing would stop the game at start. A hook that finds its spot works as before, one that does not is skipped and
 * the game runs. Without those mods nothing changes. The plugin of all three configs of the jar; a config is relaxed just
 * before its first mixin is applied, through that mixin's own config (Mixin 0.7 in LiteLoader lists only configs not yet
 * loaded, so a config looked up by name from another one was missed in some load orders).
 * - OptiFine rewrites parts of the game's renderer: a few hooks of the Forge port of malilib (EntityRenderer: the call that
 *   draws the open screen) find nothing to hook into.
 * - masa's own LiteLoader Litematica, which ours replaces, redirects some of the same calls (Discord #2); while both are
 *   installed the player is told to remove his ({@link MasaLitematicaNotice}).
 */
public final class CompatMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LogManager.getLogger("Litematica Upgraded");
    /** The configs whose hooks OptiFine is known to move. */
    private static final String[] OPTIFINE_RELAXED = {"mixins.litematica_upgraded.malilib.json"};
    /** The configs whose hooks masa's Litematica takes too. */
    private static final String[] MASA_LITEMATICA_RELAXED = {"mixins.litematica_upgraded.litematica.json"};
    /** The configs already looked at (relaxed or not). */
    private static final Set<String> CHECKED = ConcurrentHashMap.newKeySet();

    public static boolean optifinePresent() {
        ClassLoader loader = CompatMixinPlugin.class.getClassLoader();
        return loader.getResource("optifine/OptiFineForgeTweaker.class") != null || loader.getResource("net/optifine/reflect/Reflector.class") != null;
    }

    public static boolean masaLitematicaPresent() {
        return CompatMixinPlugin.class.getClassLoader().getResource("fi/dy/masa/litematica/LiteModLitematica.class") != null;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    /** Before the config's first mixin is applied: its hooks become optional when a mod that takes their spots is here. */
    private static void relaxIfNeeded(IMixinInfo mixin) {
        IMixinConfig config = mixin.getConfig();
        if (!CHECKED.add(config.getName())) return;
        if (optifinePresent() && listed(OPTIFINE_RELAXED, config)) relax(config, "OptiFine");
        if (masaLitematicaPresent() && listed(MASA_LITEMATICA_RELAXED, config)) relax(config, "masa's Litematica");
    }

    private static boolean listed(String[] names, IMixinConfig config) {
        for (String name : names) if (name.equals(config.getName())) return true;
        return false;
    }

    private static void relax(IMixinConfig config, String because) {
        try {
            Field options = config.getClass().getDeclaredField("injectorOptions");
            options.setAccessible(true);
            Object injectorOptions = options.get(config);
            Field require = injectorOptions.getClass().getDeclaredField("defaultRequireValue");
            require.setAccessible(true);
            require.setInt(injectorOptions, 0);
            LOGGER.info("{} found: the hooks of {} are optional (one it takes is skipped instead of stopping the game)", because, config.getName());
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            LOGGER.warn("{} found, but the hooks of {} could not be made optional", because, config.getName(), e);
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
        relaxIfNeeded(mixinInfo);
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    // Mixin 0.7 (LiteLoader) calls these two with its own shaded ClassNode; the methods above are the Mixin 0.8 (MixinBooter) ones.
    public void preApply(String targetClassName, org.spongepowered.asm.lib.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        relaxIfNeeded(mixinInfo);
    }

    public void postApply(String targetClassName, org.spongepowered.asm.lib.tree.ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
