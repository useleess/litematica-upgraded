package com.useleess.litematicaupgraded.selftest;

import java.lang.reflect.Method;
import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.fml.client.GuiModList;
import net.minecraftforge.fml.common.ModContainer;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.malilib.gui.config.BaseConfigScreen;
import com.useleess.litematicaupgraded.malilib.input.Keys;

/**
 * config/forge-button/: Forge's Mods list (main menu -> Mods). Our entry is picked by its name, the "Config" button by
 * its label and id, and pressed the way Forge handles a click on it.
 */
final class ForgeButtonTests {
    private static final String MOD_NAME = "Litematica Upgraded";
    /** Forge 1.12.2 labels the button "Config" without a translation and gives it id 20. */
    private static final String CONFIG_LABEL = "Config";
    private static final int CONFIG_ID = 20;

    private static GuiModList modsList;
    private static int ticks;

    private ForgeButtonTests() {}

    static void register(Runner.Registry r) {
        r.waitFor("config/forge-button/enabled-for-our-mod", 200, ForgeButtonTests::open, c -> {
            if (!ourEntrySelected(c)) return false;
            GuiButton button = configButton(c);
            c.check(button.visible && button.enabled, "the Config button is greyed out for " + MOD_NAME);
            // a few frames for the screenshot to show the selected entry
            if (++ticks < 15) return false;
            Runner.shot(c.mc, "mods-list-config");
            return true;
        });
        r.waitFor("config/forge-button/opens-our-config-screen", 200, ForgeButtonTests::open, c -> {
            if (!ourEntrySelected(c)) return false;
            pressConfig(c);
            c.check(c.mc.currentScreen instanceof BaseConfigScreen, "the Config button opened " + c.mc.currentScreen + ", not our settings screen");
            c.check(((BaseScreen) c.mc.currentScreen).getParent() == modsList, "our settings screen does not go back to the Mods list");
            return true;
        });
        r.waitFor("config/forge-button/closing-returns-to-mods-list", 200, ForgeButtonTests::open, c -> {
            if (!ourEntrySelected(c)) return false;
            pressConfig(c);
            c.check(c.mc.currentScreen instanceof BaseConfigScreen, "our settings screen did not open");
            ((BaseScreen) c.mc.currentScreen).onKeyTyped(Keys.KEY_ESCAPE, 0, 0);
            c.check(c.mc.currentScreen == modsList, "Esc on our settings screen went to " + c.mc.currentScreen + ", not the Mods list");
            c.mc.displayGuiScreen(null);
            return true;
        });
    }

    /** Each test starts with no screen open (the runner closes them), so each opens its own Mods list. */
    private static void open(Runner.Context c) {
        ticks = 0;
        modsList = new GuiModList(null);
        c.mc.displayGuiScreen(modsList);
    }

    /** The list fills and sorts itself on its first ticks, so our entry is selected a few ticks after opening. */
    private static boolean ourEntrySelected(Runner.Context c) {
        if (ticks >= 5) return true;
        if (c.mc.currentScreen != modsList || ++ticks < 5) return false;
        modsList.selectModIndex(indexOf(c, MOD_NAME));
        return true;
    }

    /** Presses the button the way Forge handles a click on it (it opens the new screen at once). */
    private static void pressConfig(Runner.Context c) throws Exception {
        Method press = ReflectionHelper.findMethod(GuiScreen.class, "actionPerformed", "func_146284_a", GuiButton.class);
        press.invoke(modsList, configButton(c));
    }

    private static int indexOf(Runner.Context c, String name) {
        List<ModContainer> mods = ReflectionHelper.getPrivateValue(GuiModList.class, modsList, "mods");
        for (int i = 0; i < mods.size(); i++) {
            if (name.equals(mods.get(i).getName())) return i;
        }
        c.check(false, name + " is not in the Mods list");
        return -1;
    }

    private static GuiButton configButton(Runner.Context c) {
        List<GuiButton> buttons = ReflectionHelper.getPrivateValue(GuiScreen.class, modsList, "buttonList", "field_146292_n");
        for (GuiButton button : buttons) {
            if (button.id == CONFIG_ID && CONFIG_LABEL.equals(button.displayString)) return button;
        }
        c.check(false, "the Mods list has no Config button");
        return null;
    }
}
