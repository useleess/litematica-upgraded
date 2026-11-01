package com.useleess.litematicaupgraded.gui;

import java.util.Set;

import org.apache.logging.log4j.LogManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.toasts.SystemToast;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.client.IModGuiFactory;

import com.useleess.litematicaupgraded.LitematicaUpgraded;
import com.useleess.litematicaupgraded.litematica.gui.ConfigScreen;

/**
 * Forge's Mods list: its "Config" button opens our settings screen, with the list as the parent so Done / Esc go back
 * to it. If the screen cannot be built the player stays on the list, gets a toast and the log gets the error.
 */
public class ModListConfigFactory implements IModGuiFactory {
    static final String FAILED_TITLE = "litematica_upgraded.toast.config_failed.title";
    static final String FAILED_TEXT = "litematica_upgraded.toast.config_failed.text";

    @Override
    public void initialize(Minecraft mc) {}

    @Override
    public boolean hasConfigGui() {
        return true;
    }

    @Override
    public GuiScreen createConfigGui(GuiScreen parent) {
        try {
            return ConfigScreen.create().setParent(parent);
        } catch (RuntimeException | LinkageError e) {
            LogManager.getLogger(LitematicaUpgraded.MOD_ID).error("The settings screen could not be opened from the Mods list", e);
            Minecraft.getMinecraft().getToastGui().add(new SystemToast(SystemToast.Type.NARRATOR_TOGGLE,
                    new TextComponentTranslation(FAILED_TITLE), new TextComponentTranslation(FAILED_TEXT)));
            return parent;
        }
    }

    @Override
    public Set<RuntimeOptionCategoryElement> runtimeGuiCategories() {
        return null;
    }
}
