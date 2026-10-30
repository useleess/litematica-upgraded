package com.useleess.litematicaupgraded.selftest;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.lwjgl.input.Keyboard;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import com.useleess.litematicaupgraded.importer.ImportCore;
import com.useleess.litematicaupgraded.importer.OldSettingsImport;
import com.useleess.litematicaupgraded.litematica.config.Configs;
import com.useleess.litematicaupgraded.litematica.config.Hotkeys;
import com.useleess.litematicaupgraded.litematica.gui.LoadedSchematicsListScreen;
import com.useleess.litematicaupgraded.litematica.gui.SchematicBrowserScreen;
import com.useleess.litematicaupgraded.malilib.config.ConfigManagerImpl;
import com.useleess.litematicaupgraded.malilib.input.HotkeyManagerImpl;
import com.useleess.litematicaupgraded.malilib.input.KeyBind;
import com.useleess.litematicaupgraded.malilib.input.KeyBindImpl;
import com.useleess.litematicaupgraded.malilib.registry.Registry;
import com.useleess.litematicaupgraded.printer.PrinterConfigs;

/**
 * import/: the one-time copy of keybinds and settings. tools/ingame.ps1 puts old config files into the game folder of
 * the plain phase before the first start: LiteLoader Litematica's litematica.json (oldest: K opens the loaded schematics,
 * infoHudMaxLines 12, materialListHudMaxLines 11, pasteCommandLimit 5000 = over its limit), Schematica Upgraded's
 * hotkeys + config (J opens the schematic projects, infoHudMaxLines 14 - newer, so it wins) and old Schematica's
 * load key in options.txt (H). They only touch actions that have no key by default and HUD line counts, and the last
 * test puts them all back to their defaults, so no other test sees them.
 */
final class ImportTests {
    private static int waitTicks;

    private ImportTests() {}

    static void register(Runner.Registry r) {
        r.add("import/keys/copied-from-litematica-schematica-upgraded-and-schematica", c -> {
            c.check(keys(Hotkeys.OPEN_LOADED_SCHEMATICS_SCREEN.getKeyBind()).equals(Arrays.asList(Keyboard.KEY_K)),
                "LiteLoader Litematica's key K was not copied: " + Hotkeys.OPEN_LOADED_SCHEMATICS_SCREEN.getKeyBind().getKeysDisplayString());
            c.check(keys(Hotkeys.OPEN_SCHEMATIC_VCS_SCREEN.getKeyBind()).equals(Arrays.asList(Keyboard.KEY_J)),
                "Schematica Upgraded's key J was not copied: " + Hotkeys.OPEN_SCHEMATIC_VCS_SCREEN.getKeyBind().getKeysDisplayString());
            c.check(keys(Hotkeys.OPEN_LOAD_SCHEMATICS_SCREEN.getKeyBind()).equals(Arrays.asList(Keyboard.KEY_H)),
                "old Schematica's key H (options.txt) was not copied: " + Hotkeys.OPEN_LOAD_SCHEMATICS_SCREEN.getKeyBind().getKeysDisplayString());
            c.check(keys(Hotkeys.OPEN_MAIN_MENU.getKeyBind()).equals(Arrays.asList(Keyboard.KEY_M)), "a key that was not in the old files changed");
        });
        r.waitFor("import/keys/copied-key-opens-the-loaded-schematics", 100, c -> press(Keyboard.KEY_K),
            c -> c.mc.currentScreen instanceof LoadedSchematicsListScreen);
        r.waitFor("import/keys/old-schematica-key-opens-the-schematic-browser", 100, c -> press(Keyboard.KEY_H),
            c -> c.mc.currentScreen instanceof SchematicBrowserScreen);

        r.add("import/settings/copied-setting-is-active-newest-file-wins", c -> {
            c.check(Configs.InfoOverlays.INFO_HUD_MAX_LINES.getIntegerValue() == 14,
                "infoHudMaxLines should be 14 (Schematica Upgraded's newer file), is " + Configs.InfoOverlays.INFO_HUD_MAX_LINES.getIntegerValue());
            c.check(Configs.InfoOverlays.MATERIAL_LIST_HUD_MAX_LINES.getIntegerValue() == 11,
                "materialListHudMaxLines should be 11 (LiteLoader Litematica), is " + Configs.InfoOverlays.MATERIAL_LIST_HUD_MAX_LINES.getIntegerValue());
        });
        r.add("import/settings/value-outside-limits-skipped", c -> {
            c.check(Configs.Generic.PASTE_COMMAND_LIMIT.getIntegerValue() == 64,
                "pasteCommandLimit 5000 is over its limit and must stay 64, is " + Configs.Generic.PASTE_COMMAND_LIMIT.getIntegerValue());
            ImportCore.Result result = OldSettingsImport.lastResult;
            c.check(result != null && result.skipped.stream().anyMatch(s -> s.contains("pasteCommandLimit")), "the skipped value was not logged");
        });

        r.add("import/once/chat-line-shown-once", c -> {
            c.check(OldSettingsImport.messagesShown == 1, "chat lines shown: " + OldSettingsImport.messagesShown);
            String line = String.valueOf(OldSettingsImport.lastMessage);
            c.check(line.contains("3 keybinds and 2 settings") && line.contains("Litematica") && line.contains("Schematica Upgraded")
                && line.contains("Schematica"), "chat line: " + line);
            c.check(!line.contains("litematica_upgraded."), "the chat line is not translated: " + line);
        });
        r.add("import/once/flag-set-and-saved", c -> {
            c.check(PrinterConfigs.OLD_SETTINGS_IMPORTED.getBooleanValue(), "the done flag is not set");
            boolean saved = false;
            File[] files = new File("config").getAbsoluteFile().listFiles((d, name) -> name.endsWith(".json"));
            for (File file : files == null ? new File[0] : files) {
                String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                saved |= text.replaceAll("\\s", "").contains("\"oldSettingsImported\":true");
            }
            c.check(saved, "the done flag is not in a config file");
        });
        r.waitFor("import/once/second-start-copies-nothing", 40, c -> {
            waitTicks = 0;
            c.check(OldSettingsImport.runOnce() == null, "the copy ran a second time");
        }, c -> {
            if (++waitTicks < 20) return false;
            c.check(OldSettingsImport.messagesShown == 1, "a second chat line came: " + OldSettingsImport.messagesShown);
            return true;
        });
        r.add("import/once/player-changes-never-overwritten", c -> {
            try {
                // the player changes two things, then the copy runs on the same old files again (flag aside)
                Configs.InfoOverlays.MATERIAL_LIST_HUD_MAX_LINES.setValue(20);
                Hotkeys.OPEN_LOADED_SCHEMATICS_SCREEN.getKeyBind().setValueFromString("L_ALT,K");
                ImportCore.Result again = OldSettingsImport.copy(OldSettingsImport.readAgain());
                c.check(Configs.InfoOverlays.MATERIAL_LIST_HUD_MAX_LINES.getIntegerValue() == 20, "a setting the player changed was overwritten");
                c.check(keys(Hotkeys.OPEN_LOADED_SCHEMATICS_SCREEN.getKeyBind()).equals(Arrays.asList(Keyboard.KEY_LMENU, Keyboard.KEY_K)),
                    "a keybind the player changed was overwritten");
                c.check(again.keys + again.settings == 0, "copied again: " + again.keys + " keys, " + again.settings + " settings");
            } finally {
                // back to the defaults, so no other test sees the sample values
                Configs.InfoOverlays.MATERIAL_LIST_HUD_MAX_LINES.resetToDefault();
                Configs.InfoOverlays.INFO_HUD_MAX_LINES.resetToDefault();
                Hotkeys.OPEN_LOADED_SCHEMATICS_SCREEN.resetToDefault();
                Hotkeys.OPEN_SCHEMATIC_VCS_SCREEN.resetToDefault();
                Hotkeys.OPEN_LOAD_SCHEMATICS_SCREEN.resetToDefault();
                ((ConfigManagerImpl) Registry.CONFIG_MANAGER).saveIfDirty();
                Registry.HOTKEY_MANAGER.updateUsedKeys();
            }
        });
    }

    private static List<Integer> keys(KeyBind keyBind) {
        IntArrayList list = new IntArrayList();
        keyBind.getKeysToList(list);
        return new ArrayList<>(list);
    }

    /** A key press + release as the game's keyboard handler passes it to malilib. */
    private static void press(int key) throws Exception {
        Method check = HotkeyManagerImpl.class.getDeclaredMethod("checkKeyBindsForChanges", int.class);
        check.setAccessible(true);
        KeyBindImpl.onKeyInputPre(key, 0, 0, (char) 0, true);
        check.invoke(Registry.HOTKEY_MANAGER, key);
        KeyBindImpl.onKeyInputPre(key, 0, 0, (char) 0, false);
        check.invoke(Registry.HOTKEY_MANAGER, key);
    }
}
