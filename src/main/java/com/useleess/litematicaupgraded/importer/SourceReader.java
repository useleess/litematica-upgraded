package com.useleess.litematicaupgraded.importer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import com.useleess.litematicaupgraded.importer.ImportCore.Entry;

/**
 * Reads the keybinds and settings of the mods players come from, out of the game folder, under our names:
 * masa's LiteLoader Litematica (liteconfig/common/, malilib's file format old and new), Schematica Upgraded
 * (config/schematica_plus_hotkeys.json + config/schematica_plus.cfg) and old Schematica (its keys in options.txt).
 * A missing, broken or huge file is skipped; nothing here throws.
 */
public final class SourceReader {
    public static final String LITEMATICA = "litematica", SCHEMATICA_UPGRADED = "schematica_upgraded", SCHEMATICA = "schematica";
    /** Our names for malilib's own options and hotkeys start with this (Litematica has some of the same names). */
    public static final String MALILIB = "malilib:";
    /** Bigger files are not config files. */
    static final long MAX_SIZE = 4L * 1024 * 1024;
    private static final Pattern CFG_LINE = Pattern.compile("^\\s*([BIDS]):\"?([^\"=]+?)\"?=(.*)$");
    private static final Pattern OPTIONS_KEY = Pattern.compile("^key_schematica\\.key\\.([A-Za-z]+):(-?\\d+)(?::([A-Z_]+))?\\s*$");

    private SourceReader() {}

    /** Everything found in the game folder. */
    public static List<Entry> read(Path gameDir, Consumer<String> log) {
        List<Entry> out = new ArrayList<>();
        readMalilib(gameDir.resolve("liteconfig/common/litematica.json"), "", LITEMATICA, out, log);
        readMalilib(gameDir.resolve("liteconfig/common/malilib.json"), MALILIB, LITEMATICA, out, log);
        readSchematicaUpgradedHotkeys(gameDir.resolve("config/schematica_plus_hotkeys.json"), out, log);
        readSchematicaUpgradedSettings(gameDir.resolve("config/schematica_plus.cfg"), out, log);
        readOptionsTxt(gameDir.resolve("options.txt"), out, log);
        return out;
    }

    /** A malilib config file (LiteLoader Litematica's litematica.json or malilib.json). */
    static void readMalilib(Path file, String prefix, String source, List<Entry> out, Consumer<String> log) {
        String text = text(file, log);
        if (text == null) return;
        long time = time(file);
        JsonObject root = object(text, file, log);
        if (root == null) return;
        for (Map.Entry<String, JsonElement> section : root.entrySet()) {
            if (!section.getValue().isJsonObject() || section.getKey().equals("Internal")) continue;
            boolean hotkeys = section.getKey().equals("Hotkeys");
            for (Map.Entry<String, JsonElement> option : section.getValue().getAsJsonObject().entrySet()) {
                String name = option.getKey();
                JsonElement value = option.getValue();
                String keys = keysOf(hotkeys, value);
                if (keys != null) {
                    List<Integer> codes = ImportRules.parseKeys(keys);
                    if (codes != null) out.add(Entry.hotkey(prefix + ImportRules.hotkey(name), codes, source, time));
                    else log.accept("Not copied: keybind " + name + " from " + file + ": unknown key in \"" + keys + "\"");
                }
                if (!hotkeys) readSetting(prefix, name, value, source, time, out);
            }
        }
    }

    private static void readSetting(String prefix, String name, JsonElement value, String source, long time, List<Entry> out) {
        String[] ours = ImportRules.setting(name).split(":", 2);
        String target = prefix + ours[0], part = ours.length > 1 ? ours[1] : "";
        if (value.isJsonPrimitive()) {
            out.add(Entry.setting(target, part, value.getAsJsonPrimitive(), source, time));
            return;
        }
        if (!value.isJsonObject()) return;
        JsonObject object = value.getAsJsonObject();
        if (name.equals("customSchematicDirectory")) {
            // only a folder the player switched on is theirs
            JsonPrimitive enabled = primitive(object, "enabled"), directory = primitive(object, "directory");
            if (enabled != null && enabled.isBoolean() && enabled.getAsBoolean() && directory != null)
                out.add(Entry.setting(target, "dir", directory, source, time));
        } else if (primitive(object, "enabled") != null) {
            out.add(Entry.setting(target, part, primitive(object, "enabled"), source, time));
        } else if (primitive(object, "b") != null) {
            out.add(Entry.setting(target, "", primitive(object, "b"), source, time));
            if (primitive(object, "d") != null) out.add(Entry.setting(target, "d", primitive(object, "d"), source, time));
            if (primitive(object, "i") != null) out.add(Entry.setting(target, "i", primitive(object, "i"), source, time));
        } else if (primitive(object, "x") != null && primitive(object, "y") != null) {
            out.add(Entry.setting(target, "x", primitive(object, "x"), source, time));
            out.add(Entry.setting(target, "y", primitive(object, "y"), source, time));
        }
    }

    /** The key list of one entry: "M,C" or {"keys": "M,C"} in the Hotkeys section, or the {"hotkey": {...}} of an on/off option. */
    private static String keysOf(boolean hotkeys, JsonElement value) {
        if (value.isJsonPrimitive()) return hotkeys && value.getAsJsonPrimitive().isString() ? value.getAsString() : null;
        if (!value.isJsonObject()) return null;
        JsonObject object = value.getAsJsonObject();
        if (object.has("hotkey") && object.get("hotkey").isJsonObject()) object = object.getAsJsonObject("hotkey");
        else if (!hotkeys) return null;
        JsonPrimitive keys = primitive(object, "keys");
        return keys != null && keys.isString() ? keys.getAsString() : null;
    }

    /** Schematica Upgraded's hotkeys: {"hotkeys": {"openGuiMainMenu": {"keys": [50]}, ...}}. */
    static void readSchematicaUpgradedHotkeys(Path file, List<Entry> out, Consumer<String> log) {
        String text = text(file, log);
        if (text == null) return;
        long time = time(file);
        JsonObject root = object(text, file, log);
        if (root == null || !root.has("hotkeys") || !root.get("hotkeys").isJsonObject()) return;
        for (Map.Entry<String, JsonElement> hotkey : root.getAsJsonObject("hotkeys").entrySet()) {
            if (!hotkey.getValue().isJsonObject() || !hotkey.getValue().getAsJsonObject().has("keys")
                    || !hotkey.getValue().getAsJsonObject().get("keys").isJsonArray()) continue;
            List<Integer> codes = new ArrayList<>();
            boolean ok = true;
            for (JsonElement key : hotkey.getValue().getAsJsonObject().getAsJsonArray("keys")) {
                if (!key.isJsonPrimitive() || !key.getAsJsonPrimitive().isNumber()) { ok = false; break; }
                double code = key.getAsDouble();
                // Schematica Upgraded keeps keyboard keys (1-255) and mouse buttons (-100 and up)
                if (code != Math.rint(code) || code >= 256 || !ImportRules.validCode((int) code)) { ok = false; break; }
                if (!codes.contains((int) code)) codes.add((int) code);
            }
            if (ok) out.add(Entry.hotkey(ImportRules.hotkey(hotkey.getKey()), codes, SCHEMATICA_UPGRADED, time));
            else log.accept("Not copied: keybind " + hotkey.getKey() + " from " + file + ": bad key code");
        }
    }

    /** Schematica Upgraded's settings: a Forge config ("    B:printerEnabled=true"). */
    static void readSchematicaUpgradedSettings(Path file, List<Entry> out, Consumer<String> log) {
        String text = text(file, log);
        if (text == null) return;
        long time = time(file);
        boolean inList = false;
        for (String line : text.split("\\r?\\n")) {
            if (inList) { inList = !line.trim().equals(">"); continue; }
            if (line.trim().endsWith("<")) { inList = true; continue; }
            Matcher m = CFG_LINE.matcher(line);
            if (!m.matches()) continue;
            String raw = m.group(3).trim();
            JsonPrimitive value = new JsonPrimitive(raw);
            try {
                if (m.group(1).equals("B") && (raw.equals("true") || raw.equals("false"))) value = new JsonPrimitive(Boolean.parseBoolean(raw));
                if (m.group(1).equals("I")) value = new JsonPrimitive(Long.parseLong(raw));
                if (m.group(1).equals("D")) value = new JsonPrimitive(Double.parseDouble(raw));
            } catch (NumberFormatException error) {
                // kept as text: it then does not fit a number option and is skipped
            }
            String[] ours = ImportRules.setting(m.group(2).trim()).split(":", 2);
            out.add(Entry.setting(ours[0], ours.length > 1 ? ours[1] : "", value, SCHEMATICA_UPGRADED, time));
        }
    }

    /** Old Schematica's keys, as the game saves them in options.txt ("key_schematica.key.load:181", Forge adds ":CONTROL"). */
    static void readOptionsTxt(Path file, List<Entry> out, Consumer<String> log) {
        String text = text(file, log);
        if (text == null) return;
        long time = time(file);
        for (String line : text.split("\\r?\\n")) {
            Matcher m = OPTIONS_KEY.matcher(line);
            if (!m.matches() || !ImportRules.OLD_SCHEMATICA_KEYS.containsKey(m.group(1))) continue;
            int code;
            try {
                code = Integer.parseInt(m.group(2));
            } catch (NumberFormatException error) {
                continue;
            }
            boolean isDefault = code == 0;
            for (int value : ImportRules.OLD_SCHEMATICA_DEFAULTS.getOrDefault(m.group(1), new int[0])) isDefault |= code == value && m.group(3) == null;
            if (isDefault || !ImportRules.validCode(code) || code >= 256) continue;
            List<Integer> keys = new ArrayList<>();
            String modifier = m.group(3);
            if ("CONTROL".equals(modifier)) keys.add(org.lwjgl.input.Keyboard.KEY_LCONTROL);
            if ("SHIFT".equals(modifier)) keys.add(org.lwjgl.input.Keyboard.KEY_LSHIFT);
            if ("ALT".equals(modifier)) keys.add(org.lwjgl.input.Keyboard.KEY_LMENU);
            keys.add(code);
            out.add(Entry.hotkey(ImportRules.OLD_SCHEMATICA_KEYS.get(m.group(1)), keys, SCHEMATICA, time));
        }
    }

    private static String text(Path file, Consumer<String> log) {
        try {
            if (!Files.isRegularFile(file)) return null;
            if (Files.size(file) > MAX_SIZE) {
                log.accept("Not copied: " + file + " is too big for a config file");
                return null;
            }
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException error) {
            log.accept("Not copied: could not read " + file + ": " + error);
            return null;
        }
    }

    private static JsonObject object(String text, Path file, Consumer<String> log) {
        try {
            JsonElement root = new JsonParser().parse(text);
            if (root.isJsonObject()) return root.getAsJsonObject();
        } catch (RuntimeException error) {
            // reported below
        }
        log.accept("Not copied: " + file + " is not a readable config file");
        return null;
    }

    private static long time(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException error) {
            return 0;
        }
    }

    private static JsonPrimitive primitive(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsJsonPrimitive() : null;
    }
}
