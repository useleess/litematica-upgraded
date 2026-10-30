package com.useleess.litematicaupgraded.importer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.useleess.litematicaupgraded.LitematicaUpgraded;
import com.useleess.litematicaupgraded.malilib.config.ConfigManagerImpl;
import com.useleess.litematicaupgraded.malilib.registry.Registry;
import com.useleess.litematicaupgraded.printer.PrinterConfigs;

/**
 * On the first start (once, never again: {@link PrinterConfigs#OLD_SETTINGS_IMPORTED}) copies the keybinds and settings
 * of the mods the player used before, and says so in one chat line on the first world join.
 */
public final class OldSettingsImport {
    public static final OldSettingsImport INSTANCE = new OldSettingsImport();
    static final Logger LOG = LogManager.getLogger(LitematicaUpgraded.MOD_ID);
    private static Path gameDir;
    /** What the old files held when the game started (before the game rewrites options.txt without old Schematica's keys). */
    private static List<ImportCore.Entry> found = new ArrayList<>();
    private static boolean checked;
    private static TextComponentTranslation pending;
    /** For the in-game tests: how many chat lines were shown, the last one, the last copy's result. */
    public static int messagesShown;
    public static String lastMessage;
    public static ImportCore.Result lastResult;

    private OldSettingsImport() {}

    /** At the mod's pre-init: reads the old files. */
    public static void readOldFiles(Path gameDirectory) {
        gameDir = gameDirectory;
        found = SourceReader.read(gameDirectory, LOG::info);
    }

    /** Copies once, unless done before; returns what was copied, or null when it was done before. */
    public static ImportCore.Result runOnce() {
        if (PrinterConfigs.OLD_SETTINGS_IMPORTED.getBooleanValue()) return null;
        ImportCore.Result result;
        try {
            result = copy(found);
        } finally {
            // done also when nothing was found or something went wrong: it never runs again
            PrinterConfigs.OLD_SETTINGS_IMPORTED.setBooleanValue(true);
            ((ConfigManagerImpl) Registry.CONFIG_MANAGER).saveIfDirty();
            // the hotkey table is built from the keys: rebuild it, or a copied key does nothing until the next start
            Registry.HOTKEY_MANAGER.updateUsedKeys();
            found = new ArrayList<>();
        }
        if (result.keys + result.settings > 0) {
            List<String> names = new ArrayList<>();
            for (String source : result.sources) names.add(I18n.format("litematica_upgraded.message.import.source." + source));
            pending = new TextComponentTranslation("litematica_upgraded.message.import.copied", result.keys, result.settings, String.join(", ", names));
        }
        return result;
    }

    /** The copy itself (no flag, no saving): what the in-game tests run a second time. */
    public static ImportCore.Result copy(List<ImportCore.Entry> entries) {
        ImportCore.Result result = ImportCore.apply(entries, new LiveTarget(gameDir), LOG::info);
        if (result.keys + result.settings > 0)
            LOG.info("Copied {} keybinds and {} settings from {}", result.keys, result.settings, String.join(", ", result.sources));
        lastResult = result;
        return result;
    }

    /** The old files again, as they are now. */
    public static List<ImportCore.Entry> readAgain() {
        return SourceReader.read(gameDir, LOG::info);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        // the first tick comes after malilib loaded the config files, so "already changed" is known
        if (!checked) {
            checked = true;
            try {
                runOnce();
            } catch (RuntimeException error) {
                LOG.warn("Could not copy the old keybinds and settings", error);
            }
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (pending != null && mc.player != null && mc.world != null && mc.ingameGUI != null) {
            mc.ingameGUI.getChatGUI().printChatMessage(pending);
            lastMessage = pending.getUnformattedText();
            messagesShown++;
            pending = null;
        }
    }
}
