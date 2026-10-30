package com.useleess.litematicaupgraded.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.lwjgl.input.Keyboard;

import com.google.gson.JsonPrimitive;

import com.useleess.litematicaupgraded.importer.ImportCore.Entry;
import com.useleess.litematicaupgraded.importer.ImportCore.Outcome;

/** The one-time copy of keybinds and settings from LiteLoader Litematica, Schematica Upgraded and old Schematica. */
public class ImportTest {
    private Path dir;
    private final List<String> log = new ArrayList<>();

    @Before
    public void folder() throws IOException {
        dir = Files.createTempDirectory("lu-import-test");
    }

    @After
    public void clean() throws IOException {
        Files.walk(dir).sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> p.toFile().delete());
    }

    /** Settings: name -> {value, default, min, max}; hotkeys: name -> keys (default = empty). */
    static final class FakeTarget implements ImportCore.Target {
        final Map<String, Object> values = new LinkedHashMap<>();
        final Map<String, Object> defaults = new LinkedHashMap<>();
        final Map<String, List<Integer>> keys = new LinkedHashMap<>();
        final Map<String, List<Integer>> defaultKeys = new LinkedHashMap<>();

        FakeTarget setting(String name, Object value) {
            values.put(name, value);
            defaults.put(name, value);
            return this;
        }

        FakeTarget hotkey(String name, Integer... codes) {
            keys.put(name, Arrays.asList(codes));
            defaultKeys.put(name, Arrays.asList(codes));
            return this;
        }

        @Override public boolean hasSetting(String name) { return values.containsKey(name); }
        @Override public boolean settingModified(String name) { return !values.get(name).equals(defaults.get(name)); }

        @Override
        public Outcome setSetting(String name, String part, JsonPrimitive value) {
            Object now = values.get(name);
            Object next;
            if (now instanceof Boolean && part.isEmpty()) { if (!value.isBoolean()) return Outcome.BAD; next = value.getAsBoolean(); }
            else if (now instanceof Integer && part.isEmpty()) {
                if (!value.isNumber() || value.getAsDouble() != Math.rint(value.getAsDouble()) || value.getAsDouble() < 1 || value.getAsDouble() > 100) return Outcome.BAD;
                next = value.getAsInt();
            }
            else if (now instanceof String && part.isEmpty()) { if (!value.isString()) return Outcome.BAD; next = value.getAsString(); }
            else if (now instanceof double[]) {
                // a two-part option {on/off, number 0..1} stored as {b, d}
                double[] pair = ((double[]) now).clone();
                if (part.isEmpty() && value.isBoolean()) pair[0] = value.getAsBoolean() ? 1 : 0;
                else if (!part.isEmpty() && "dxy".contains(part) && value.isNumber() && value.getAsDouble() >= 0 && value.getAsDouble() <= 1000) pair[part.equals("y") ? 1 : part.equals("x") ? 0 : 1] = value.getAsDouble();
                else return Outcome.BAD;
                if (Arrays.equals(pair, (double[]) now)) return Outcome.SAME;
                values.put(name, pair);
                return Outcome.APPLIED;
            }
            else return Outcome.BAD;
            if (next.equals(now)) return Outcome.SAME;
            values.put(name, next);
            return Outcome.APPLIED;
        }

        @Override public List<String> keyNames() { return new ArrayList<>(keys.keySet()); }
        @Override public boolean keyModified(String name) { return !keys.get(name).equals(defaultKeys.get(name)); }
        @Override public List<Integer> keys(String name) { return keys.get(name); }
        @Override public void setKeys(String name, List<Integer> codes) { keys.put(name, codes); }
    }

    private Path write(String relative, String text, long ageMinutes) throws IOException {
        Path file = dir.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, text.getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - ageMinutes * 60_000L));
        return file;
    }

    private static List<Integer> keys(Integer... codes) {
        return Arrays.asList(codes);
    }

    // --- key names ---

    @Test
    public void keyNamesOfBothMalilibVersions() {
        assertEquals(keys(Keyboard.KEY_LMENU, Keyboard.KEY_M), ImportRules.parseKeys("L_ALT,M"));
        assertEquals(keys(Keyboard.KEY_LMENU), ImportRules.parseKeys("LMENU"));
        assertEquals(keys(Keyboard.KEY_LCONTROL, Keyboard.KEY_RCONTROL), ImportRules.parseKeys("LCONTROL, R_CTRL"));
        assertEquals(keys(-100, -99, -98), ImportRules.parseKeys("MOUSE0,BUTTON1,MIDDLE_MOUSE"));
        assertEquals(keys(-100), ImportRules.parseKeys("MOUSE_LEFT"));
        assertEquals(keys(Keyboard.KEY_PRIOR, Keyboard.KEY_NEXT), ImportRules.parseKeys("PAGE_UP,PAGE_DOWN"));
        assertEquals(keys(35 + 256), ImportRules.parseKeys("CHAR_35"));
        assertEquals(keys(-199, -201), ImportRules.parseKeys("SCROLL_UP,SCROLL_DOWN"));
        assertEquals(keys(Keyboard.KEY_F3, Keyboard.KEY_M), ImportRules.parseKeys("f3,m"));
        assertEquals(keys(), ImportRules.parseKeys(""));
        assertNull("an unknown key keeps the hotkey as it is", ImportRules.parseKeys("M,NOT_A_KEY"));
        assertNull(ImportRules.parseKeys("BUTTON77"));
    }

    @Test
    public void everyRenameEndsOnOneOfOurNames() {
        // our names are the newer Litematica names: an old name never maps onto another old name
        for (String ours : ImportRules.KEYS.values()) assertFalse(ours, ImportRules.KEYS.containsKey(ours));
        for (String ours : ImportRules.SETTINGS.values()) assertFalse(ours, ImportRules.SETTINGS.containsKey(ours.split(":")[0]));
        assertEquals("openMainMenuScreen", ImportRules.hotkey("openGuiMainMenu"));
        assertEquals("openMainMenuScreen", ImportRules.hotkey("openMainMenuScreen"));
        assertEquals("easyPlaceMode", ImportRules.hotkey("easyPlaceToggle"));
        assertEquals("mainRenderingToggle", ImportRules.hotkey("enableRendering"));
        assertEquals("printerEnabled", ImportRules.hotkey("printerToggle"));
        assertEquals("translucentSchematicRendering:d", ImportRules.setting("alpha"));
        assertEquals("pasteCommandLimit", ImportRules.setting("commandLimitPerTick"));
        assertEquals("printerBlocksPerTick", ImportRules.setting("placeBlocksPerTick"));
        assertEquals("customSchematicDirectory:dir", ImportRules.setting("schematicDirectory"));
    }

    // --- LiteLoader Litematica (malilib 0.5x, old and new format) ---

    @Test
    public void liteLoaderOldFormat() throws IOException {
        Path file = write("liteconfig/common/litematica.json", "{\"Generic\": {\"easyPlaceMode\": true, \"easyPlaceHoldEnabled\": true,"
            + " \"commandLimitPerTick\": 24, \"pickBlockShulkers\": true},"
            + " \"Visuals\": {\"enableRendering\": false, \"alphaEnabled\": true, \"alpha\": 0.4, \"blockInfoLinesOffsetX\": 7},"
            + " \"Hotkeys\": {\"openGuiMainMenu\": \"N\", \"easyPlaceToggle\": \"L_ALT,E\", \"openGuiSettings\": {\"keys\": \"LMENU,C\"},"
            + " \"rerenderSchematic\": \"NOT_A_KEY\"}}", 5);
        List<Entry> found = new ArrayList<>();
        SourceReader.readMalilib(file, "", SourceReader.LITEMATICA, found, log::add);
        Map<String, Object> got = flat(found);
        assertEquals(true, got.get("easyPlaceMode"));
        assertEquals(true, got.get("easyPlaceHold"));
        assertEquals(24, ((Number) got.get("pasteCommandLimit")).intValue());
        assertEquals(true, got.get("pickBlockShulkerBoxes"));
        assertEquals(false, got.get("mainRenderingToggle"));
        assertEquals(true, got.get("translucentSchematicRendering"));
        assertEquals(0.4, ((Number) got.get("translucentSchematicRendering:d")).doubleValue(), 1e-9);
        assertEquals(keys(Keyboard.KEY_N), got.get("key openMainMenuScreen"));
        assertEquals(keys(Keyboard.KEY_LMENU, Keyboard.KEY_E), got.get("key easyPlaceMode"));
        assertEquals(keys(Keyboard.KEY_LMENU, Keyboard.KEY_C), got.get("key openConfigScreen"));
        assertFalse(got.containsKey("key refreshSchematicRenderer"));
        assertTrue(String.join("\n", log), log.stream().anyMatch(l -> l.contains("NOT_A_KEY")));
    }

    @Test
    public void liteLoaderNewFormat() throws IOException {
        Path file = write("liteconfig/common/litematica.json", "{\"Generic\": {\"easyPlaceMode\": {\"enabled\": true, \"hotkey\": {\"keys\": \"R_SHIFT,E\"}},"
            + " \"customSchematicDirectory\": {\"enabled\": true, \"directory\": \"D:/builds\"}, \"defaultAreaSelectionMode\": \"normal\"},"
            + " \"Visuals\": {\"placementBoxSides\": {\"b\": true, \"d\": 0.6}},"
            + " \"InfoOverlays\": {\"toolHudOffset\": {\"x\": 5, \"y\": 9}},"
            + " \"Hotkeys\": {\"openMainMenuScreen\": {\"keys\": \"M\", \"settings\": {\"activate_on\": \"release\"}}},"
            + " \"Internal\": {\"secret\": 1}}", 5);
        List<Entry> found = new ArrayList<>();
        SourceReader.readMalilib(file, "", SourceReader.LITEMATICA, found, log::add);
        Map<String, Object> got = flat(found);
        assertEquals(true, got.get("easyPlaceMode"));
        assertEquals(keys(Keyboard.KEY_RSHIFT, Keyboard.KEY_E), got.get("key easyPlaceMode"));
        assertEquals("D:/builds", got.get("customSchematicDirectory:dir"));
        assertFalse("a folder is copied only as the folder", got.containsKey("customSchematicDirectory"));
        assertEquals("normal", got.get("defaultAreaSelectionMode"));
        assertEquals(true, got.get("placementBoxSides"));
        assertEquals(0.6, ((Number) got.get("placementBoxSides:d")).doubleValue(), 1e-9);
        assertEquals(5, ((Number) got.get("toolHudOffset:x")).intValue());
        assertEquals(9, ((Number) got.get("toolHudOffset:y")).intValue());
        assertEquals(keys(Keyboard.KEY_M), got.get("key openMainMenuScreen"));
        assertFalse(got.containsKey("secret"));
    }

    @Test
    public void malilibOwnFileGetsItsOwnNames() throws IOException {
        Path file = write("liteconfig/common/malilib.json", "{\"Generic\": {\"hoverTextMaxWidth\": 400}, \"Hotkeys\": {\"openConfigScreen\": \"A,X\"}}", 5);
        List<Entry> found = new ArrayList<>();
        SourceReader.readMalilib(file, SourceReader.MALILIB, SourceReader.LITEMATICA, found, log::add);
        Map<String, Object> got = flat(found);
        assertEquals(400, ((Number) got.get("malilib:hoverTextMaxWidth")).intValue());
        assertEquals(keys(Keyboard.KEY_A, Keyboard.KEY_X), got.get("key malilib:openConfigScreen"));
    }

    // --- Schematica Upgraded ---

    @Test
    public void schematicaUpgradedFiles() throws IOException {
        write("config/schematica_plus_hotkeys.json", "{\"hotkeys\": {\"openGuiMainMenu\": {\"keys\": [50, 46], \"settings\": {}},"
            + " \"openGuiSchematicProjects\": {\"keys\": [-98]}, \"layerNext\": {\"keys\": []}, \"broken\": {\"keys\": [9999]}}}", 3);
        write("config/schematica_plus.cfg", "# Configuration file\n\ngeneral {\n    D:alpha=0.75\n    B:alphaEnabled=true\n    B:highlightAir=yes\n"
            + "    S:extraAirBlocks <\n        minecraft:glass\n     >\n    I:placeBlocksPerTick=5\n    I:commandLimitPerTick=abc\n"
            + "    S:schematicDirectory=./schem\n    B:\"printBreakWrongBlock\"=true\n}\n", 3);
        List<Entry> found = SourceReader.read(dir, log::add);
        Map<String, Object> got = flat(found);
        assertEquals(keys(Keyboard.KEY_M, Keyboard.KEY_C), got.get("key openMainMenuScreen"));
        assertEquals(keys(-98), got.get("key openSchematicVCSScreen"));
        assertEquals(keys(), got.get("key layerNext"));
        assertFalse(got.containsKey("key broken"));
        assertEquals(0.75, ((Number) got.get("translucentSchematicRendering:d")).doubleValue(), 1e-9);
        assertEquals(true, got.get("translucentSchematicRendering"));
        assertEquals("yes", got.get("schematicOverlayTypeExtra"));
        assertFalse("list entries are not options", got.containsKey("minecraft:glass"));
        assertEquals(5L, ((Number) got.get("printerBlocksPerTick")).longValue());
        assertEquals("abc", got.get("pasteCommandLimit"));
        assertEquals("./schem", got.get("customSchematicDirectory:dir"));
        assertEquals(true, got.get("printerBreakWrongBlock"));
        for (Entry entry : found) assertEquals(SourceReader.SCHEMATICA_UPGRADED, entry.source);
    }

    // --- old Schematica (options.txt) ---

    @Test
    public void oldSchematicaKeysFromOptionsTxt() throws IOException {
        write("options.txt", "version:1343\nkey_key.attack:-100\nkey_schematica.key.load:" + Keyboard.KEY_DIVIDE + "\nkey_schematica.key.save:"
            + Keyboard.KEY_K + "\nkey_schematica.key.control:" + Keyboard.KEY_J + ":CONTROL\nkey_schematica.key.layerInc:0\n"
            + "key_schematica.key.manipulate:-98\nkey_schematica.key.printerToggle:" + Keyboard.KEY_P + "\nkey_schematica.key.unknown:20\n", 1);
        List<Entry> found = SourceReader.read(dir, log::add);
        Map<String, Object> got = flat(found);
        assertFalse("Schematica's own default is not a choice", got.containsKey("key openLoadSchematicsScreen"));
        assertEquals(keys(Keyboard.KEY_K), got.get("key saveSchematicToFile"));
        assertEquals(keys(Keyboard.KEY_LCONTROL, Keyboard.KEY_J), got.get("key openMainMenuScreen"));
        assertFalse("unbound is not a choice", got.containsKey("key layerNext"));
        assertEquals(keys(-98), got.get("key openPlacementSettingsScreen"));
        assertEquals(keys(Keyboard.KEY_P), got.get("key printerEnabled"));
        assertEquals(4, found.size());
    }

    // --- broken / huge files ---

    @Test
    public void brokenAndHugeFilesAreSkipped() throws IOException {
        write("liteconfig/common/litematica.json", "{\"Generic\": {\"easyPlaceMode\": tru", 1);
        write("config/schematica_plus_hotkeys.json", "[1, 2, 3]", 1);
        byte[] big = new byte[(int) SourceReader.MAX_SIZE + 10];
        Arrays.fill(big, (byte) ' ');
        Files.createDirectories(dir.resolve("config"));
        Files.write(dir.resolve("config/schematica_plus.cfg"), big);
        Files.createDirectories(dir.resolve("options.txt"));
        List<Entry> found = SourceReader.read(dir, log::add);
        assertTrue(found.isEmpty());
        assertEquals(String.join("\n", log), 3, log.size());
        assertTrue(SourceReader.read(dir.resolve("nothing-here"), log::add).isEmpty());
    }

    // --- the copy ---

    @Test
    public void newestFileWins() throws IOException {
        write("liteconfig/common/litematica.json", "{\"InfoOverlays\": {\"infoHudMaxLines\": 12}, \"Hotkeys\": {\"openGuiMainMenu\": \"N\"}}", 60);
        write("config/schematica_plus.cfg", "general {\n    I:infoHudMaxLines=14\n}\n", 30);
        write("config/schematica_plus_hotkeys.json", "{\"hotkeys\": {\"openGuiMainMenu\": {\"keys\": [" + Keyboard.KEY_B + "]}}}", 90);
        FakeTarget target = new FakeTarget().setting("infoHudMaxLines", 10).hotkey("openMainMenuScreen", Keyboard.KEY_M);
        ImportCore.Result result = ImportCore.apply(SourceReader.read(dir, log::add), target, log::add);
        assertEquals(14, target.values.get("infoHudMaxLines"));
        assertEquals(keys(Keyboard.KEY_N), target.keys.get("openMainMenuScreen"));
        assertEquals(1, result.settings);
        assertEquals(1, result.keys);
        assertEquals(new HashSet<>(Arrays.asList(SourceReader.LITEMATICA, SourceReader.SCHEMATICA_UPGRADED)), result.sources);
    }

    @Test
    public void whatThePlayerChangedIsNeverOverwritten() {
        FakeTarget target = new FakeTarget().setting("infoHudMaxLines", 10).setting("easyPlaceMode", false)
            .hotkey("openMainMenuScreen", Keyboard.KEY_M).hotkey("layerNext", Keyboard.KEY_PRIOR);
        target.values.put("infoHudMaxLines", 20);
        target.keys.put("openMainMenuScreen", keys(Keyboard.KEY_O));
        List<Entry> found = Arrays.asList(
            Entry.setting("infoHudMaxLines", "", new JsonPrimitive(12), SourceReader.LITEMATICA, 1),
            Entry.setting("easyPlaceMode", "", new JsonPrimitive(true), SourceReader.LITEMATICA, 1),
            Entry.hotkey("openMainMenuScreen", keys(Keyboard.KEY_N), SourceReader.LITEMATICA, 1),
            Entry.hotkey("layerNext", keys(Keyboard.KEY_U), SourceReader.LITEMATICA, 1));
        ImportCore.Result result = ImportCore.apply(found, target, log::add);
        assertEquals(20, target.values.get("infoHudMaxLines"));
        assertEquals(true, target.values.get("easyPlaceMode"));
        assertEquals(keys(Keyboard.KEY_O), target.keys.get("openMainMenuScreen"));
        assertEquals(keys(Keyboard.KEY_U), target.keys.get("layerNext"));
        assertEquals(1, result.settings);
        assertEquals(1, result.keys);
        assertEquals(2, result.skipped.size());
    }

    @Test
    public void valuesThatDoNotFitAreSkipped() {
        FakeTarget target = new FakeTarget().setting("pasteCommandLimit", 64).setting("easyPlaceMode", false).setting("dateFormat", "x")
            .setting("toolHudOffset", new double[] {1, 1});
        List<Entry> found = Arrays.asList(
            Entry.setting("pasteCommandLimit", "", new JsonPrimitive(5000), SourceReader.LITEMATICA, 1),
            Entry.setting("pasteCommandLimit", "", new JsonPrimitive(2.5), SourceReader.SCHEMATICA_UPGRADED, 0),
            Entry.setting("easyPlaceMode", "", new JsonPrimitive("yes"), SourceReader.LITEMATICA, 1),
            Entry.setting("dateFormat", "", new JsonPrimitive(3), SourceReader.LITEMATICA, 1),
            Entry.setting("notOurs", "", new JsonPrimitive(3), SourceReader.LITEMATICA, 1),
            Entry.setting("toolHudOffsetY", "", new JsonPrimitive(7), SourceReader.LITEMATICA, 1));
        ImportCore.Result result = ImportCore.apply(found, target, log::add);
        assertEquals(64, target.values.get("pasteCommandLimit"));
        assertEquals(false, target.values.get("easyPlaceMode"));
        assertEquals("x", target.values.get("dateFormat"));
        assertEquals(7.0, ((double[]) target.values.get("toolHudOffset"))[1], 1e-9);
        assertEquals(1, result.settings);
        assertEquals(3, result.skipped.size());
    }

    @Test
    public void clashingKeysKeepTheFirst() {
        FakeTarget target = new FakeTarget().hotkey("openMainMenuScreen", Keyboard.KEY_M).hotkey("openConfigScreen", Keyboard.KEY_M, Keyboard.KEY_C)
            .hotkey("layerNext").hotkey("layerPrevious").hotkey("executeOperation", Keyboard.KEY_X);
        List<Entry> found = Arrays.asList(
            // two copied ones on K: the first in our list keeps it
            Entry.hotkey("layerPrevious", keys(Keyboard.KEY_K), SourceReader.LITEMATICA, 1),
            Entry.hotkey("layerNext", keys(Keyboard.KEY_K), SourceReader.LITEMATICA, 1),
            // a key another action keeps (M,C stays on openConfigScreen): not copied, so it keeps X ...
            Entry.hotkey("executeOperation", keys(Keyboard.KEY_C, Keyboard.KEY_M), SourceReader.LITEMATICA, 1),
            // ... and X is then not free for this one
            Entry.hotkey("openMainMenuScreen", keys(Keyboard.KEY_X), SourceReader.LITEMATICA, 1));
        ImportCore.Result result = ImportCore.apply(found, target, log::add);
        assertEquals(keys(Keyboard.KEY_K), target.keys.get("layerNext"));
        assertEquals(keys(), target.keys.get("layerPrevious"));
        assertEquals(keys(Keyboard.KEY_X), target.keys.get("executeOperation"));
        assertEquals(keys(Keyboard.KEY_M), target.keys.get("openMainMenuScreen"));
        assertEquals(1, result.keys);
        assertEquals(3, result.skipped.size());
        // ... and nothing ends up with two actions on one key
        Set<List<Integer>> seen = new HashSet<>();
        int bound = 0;
        for (List<Integer> k : target.keys.values()) if (!k.isEmpty()) { bound++; seen.add(k); }
        assertEquals("two actions on one key", bound, seen.size());
    }

    @Test
    public void swappedKeysBothMove() {
        FakeTarget target = new FakeTarget().hotkey("layerNext", Keyboard.KEY_PRIOR).hotkey("layerPrevious", Keyboard.KEY_NEXT);
        List<Entry> found = Arrays.asList(
            Entry.hotkey("layerNext", keys(Keyboard.KEY_NEXT), SourceReader.SCHEMATICA, 1),
            Entry.hotkey("layerPrevious", keys(Keyboard.KEY_PRIOR), SourceReader.SCHEMATICA, 1));
        ImportCore.Result result = ImportCore.apply(found, target, log::add);
        assertEquals(keys(Keyboard.KEY_NEXT), target.keys.get("layerNext"));
        assertEquals(keys(Keyboard.KEY_PRIOR), target.keys.get("layerPrevious"));
        assertEquals(2, result.keys);
        assertEquals(Collections.singleton(SourceReader.SCHEMATICA), result.sources);
    }

    @Test
    public void sameValuesAreNotCounted() {
        FakeTarget target = new FakeTarget().setting("infoHudMaxLines", 10).hotkey("openMainMenuScreen", Keyboard.KEY_M);
        List<Entry> found = Arrays.asList(
            Entry.setting("infoHudMaxLines", "", new JsonPrimitive(10), SourceReader.LITEMATICA, 1),
            Entry.hotkey("openMainMenuScreen", keys(Keyboard.KEY_M), SourceReader.LITEMATICA, 1));
        ImportCore.Result result = ImportCore.apply(found, target, log::add);
        assertEquals(0, result.keys + result.settings);
        assertTrue(result.sources.isEmpty());
    }

    private static Map<String, Object> flat(List<Entry> found) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Entry entry : found) {
            String id = entry.keys != null ? "key " + entry.name : entry.name + (entry.part.isEmpty() ? "" : ":" + entry.part);
            Object value = entry.keys != null ? entry.keys
                : entry.value.isBoolean() ? (Object) entry.value.getAsBoolean() : entry.value.isNumber() ? entry.value.getAsNumber() : entry.value.getAsString();
            out.put(id, value);
        }
        return out;
    }
}
