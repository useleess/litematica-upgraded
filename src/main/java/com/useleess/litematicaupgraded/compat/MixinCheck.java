package com.useleess.litematicaupgraded.compat;

import org.apache.logging.log4j.LogManager;

import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.relauncher.Side;

/**
 * The mod brings no Mixin of its own: it uses MixinBooter's, or LiteLoader's when LiteLoader is installed (its litemod.json
 * lists the same configs). With neither, the game stops here with a clear message instead of an unreadable error later.
 */
public final class MixinCheck {
    public static final String MESSAGE_KEY = "litematica_upgraded.error.no_mixin";
    public static final String ENGLISH = "Litematica Upgraded needs MixinBooter (or LiteLoader). Install MixinBooter into the mods folder.";

    private MixinCheck() {
    }

    /** Whether any Mixin (MixinBooter's or LiteLoader's) is in this game. */
    public static boolean mixinPresent() {
        return MixinCheck.class.getClassLoader().getResource("org/spongepowered/asm/mixin/Mixins.class") != null;
    }

    /** Stops the game with a readable message when no Mixin is installed. */
    public static void require() {
        if (mixinPresent()) return;
        LogManager.getLogger("Litematica Upgraded").fatal(ENGLISH);
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) throw ClientError.create();
        throw new IllegalStateException(ENGLISH);
    }
}
