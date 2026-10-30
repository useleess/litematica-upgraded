package com.useleess.litematicaupgraded.importer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.lwjgl.input.Keyboard;

/**
 * The names of the mods players come from, translated to ours. Our names are the newer Litematica names, so these maps
 * run the other way round from Schematica Upgraded's (which kept the older ones). Ported from Schematica Upgraded (MIT).
 */
public final class ImportRules {
    /** Older Litematica / Schematica Upgraded hotkey names -> ours (a name that is already ours is kept). */
    static final Map<String, String> KEYS = new HashMap<>();
    static {
        pairs(KEYS,
            "openGuiMainMenu", "openMainMenuScreen", "openGuiSettings", "openConfigScreen",
            "openGuiMaterialList", "openMaterialListScreen", "openGuiSchematicPlacements", "openPlacementsListScreen",
            "openGuiSchematicVerifier", "openSchematicVerifierScreen", "openGuiSelectionManager", "openAreaSelectionBrowserScreen",
            "openGuiAreaSettings", "openAreaEditorScreen", "openGuiPlacementSettings", "openPlacementSettingsScreen",
            "openGuiLoadedSchematics", "openLoadedSchematicsListScreen", "openGuiSchematicProjects", "openSchematicVCSScreen",
            "openGuiLoadSchematics", "openLoadSchematicsScreen",
            "saveAreaAsSchematicToFile", "saveSchematicToFile", "saveAreaAsInMemorySchematic", "createSchematicInMemory",
            "easyPlaceUseKey", "easyPlaceActivation", "renderInfoOverlay", "renderBlockInfoOverlay",
            "rerenderSchematic", "refreshSchematicRenderer", "selectionModeCycle", "subModeCycle",
            "invertGhostBlockRenderState", "invertSchematicRenderState", "toggleTranslucentRendering", "translucentSchematicRendering",
            "pickBlockAutoToggle", "pickBlockAuto",
            // the hotkeys of on/off options (older Litematica kept them apart from the option)
            "easyPlaceToggle", "easyPlaceMode", "pickBlockToggle", "pickBlock", "togglePlacementRestriction", "placementRestriction",
            "toggleSignTextPaste", "signTextPaste", "toolEnabledToggle", "toolItemEnabled",
            "toggleAllRendering", "mainRenderingToggle", "toggleSchematicRendering", "schematicRendering",
            "toggleSchematicBlockRendering", "schematicBlocksRendering", "toggleOverlayRendering", "schematicOverlayRendering",
            "toggleAreaSelectionBoxesRendering", "areaSelectionRendering", "togglePlacementBoxesRendering", "placementBoundingBoxRendering",
            "schematicOverlayRenderThroughBlocks", "schematicOverlayRenderThrough",
            "toggleInfoOverlayRendering", "blockInfoOverlay", "toggleVerifierOverlayRendering", "verifierOverlay",
            // Schematica Upgraded's own printer toggle
            "printerToggle", "printerEnabled", "togglePrinter", "printerEnabled");
    }

    /** Older Litematica / Schematica Upgraded option names -> ours; "name:part" for one part of a two-value option. */
    static final Map<String, String> SETTINGS = new HashMap<>();
    static {
        pairs(SETTINGS,
            // Generic
            "easyPlaceHoldEnabled", "easyPlaceHold", "generateLowercaseNames", "generateLowerCaseNames",
            "materialListIgnoreState", "materialListIgnoreBlockState", "materialListContainerScan", "materialListFromContainer",
            "commandTaskInterval", "pasteCommandInterval", "commandLimitPerTick", "pasteCommandLimit", "pickBlockEnabled", "pickBlock",
            "defaultSelectionMode", "defaultAreaSelectionMode", "pickBlockShulkers", "pickBlockShulkerBoxes",
            "schematicDirectory", "customSchematicDirectory:dir",
            // InfoOverlays
            "blockInfoLinesEnabled", "blockInfoLines", "blockInfoOverlayEnabled", "blockInfoOverlay", "verifierOverlayEnabled", "verifierOverlay",
            "verifierErrorHilightAlpha", "verifierErrorHighlightAlpha", "verifierErrorHilightMaxPositions", "verifierErrorHighlightMaxPositions",
            // Visuals
            "renderAreaSelectionBoxSides", "areaSelectionBoxSides", "enableAreaSelectionBoxesRendering", "areaSelectionRendering",
            "enableRendering", "mainRenderingToggle", "enableSchematicBlocksRendering", "schematicBlocksRendering",
            "highlight", "schematicOverlayRendering", "enableSchematicOverlay", "schematicOverlayRendering",
            "enableSchematicRendering", "schematicRendering", "schematicOverlayRenderThroughBlocks", "schematicOverlayRenderThrough",
            "highlightAir", "schematicOverlayTypeExtra", "enablePlacementBoxesRendering", "placementBoundingBoxRendering",
            "drawLines", "schematicOverlayOutlines", "schematicOverlayEnableOutlines", "schematicOverlayOutlines",
            "drawQuads", "schematicOverlaySides", "schematicOverlayEnableSides", "schematicOverlaySides",
            "renderErrorMarkerConnections", "verifierHighlightConnections", "renderErrorMarkerSides", "verifierHighlightSides",
            "renderTranslucentBlockInnerSides", "translucentBlockInnerSides",
            "alphaEnabled", "translucentSchematicRendering", "renderBlocksAsTranslucent", "translucentSchematicRendering",
            "alpha", "translucentSchematicRendering:d", "ghostBlockAlpha", "translucentSchematicRendering:d",
            "renderPlacementBoxSides", "placementBoxSides", "placementBoxSideAlpha", "placementBoxSides:d",
            "renderPlacementEnclosingBoxSides", "placementEnclosingBoxSides",
            // Colors
            "areaSelectionBoxSideColor", "areaSelectionBoxSide", "materialListHudItemCountsColor", "materialListHudItemCounts",
            "schematicRebuildBreakPlaceOverlayColor", "schematicEditBreakPlaceOverlay", "schematicRebuildReplaceOverlayColor", "schematicEditReplaceOverlay",
            "schematicOverlayColorExtra", "schematicOverlayExtra", "schematicOverlayColorMissing", "schematicOverlayMissing",
            "schematicOverlayColorWrongBlock", "schematicOverlayWrongBlock", "schematicOverlayColorWrongState", "schematicOverlayWrongState",
            // Schematica Upgraded's printer
            "placeBlocksPerTick", "printerBlocksPerTick", "printUseTools", "printerUseTools",
            "printBreakWrongBlock", "printerBreakWrongBlock", "printBreakExtraBlock", "printerBreakExtraBlock",
            "printBreakWrongStateBlock", "printerBreakWrongStateBlock");
    }

    /** Old Schematica's keys in options.txt (key_schematica.key.NAME) -> our hotkey. */
    static final Map<String, String> OLD_SCHEMATICA_KEYS = new LinkedHashMap<>();
    /** The key each one had by default (old Schematica, and Schematica Upgraded's former defaults): not a choice of the player. */
    static final Map<String, int[]> OLD_SCHEMATICA_DEFAULTS = new HashMap<>();
    static {
        pairs(OLD_SCHEMATICA_KEYS,
            "load", "openLoadSchematicsScreen", "save", "saveSchematicToFile", "control", "openMainMenuScreen",
            "manipulate", "openPlacementSettingsScreen", "layerInc", "layerNext", "layerDec", "layerPrevious",
            "layerToggle", "layerModeNext", "renderToggle", "mainRenderingToggle", "printerToggle", "printerEnabled",
            "pickBlock", "pickBlockFirst", "execute", "executeOperation");
        OLD_SCHEMATICA_DEFAULTS.put("load", new int[] {Keyboard.KEY_DIVIDE});
        OLD_SCHEMATICA_DEFAULTS.put("save", new int[] {Keyboard.KEY_MULTIPLY, Keyboard.KEY_N});
        OLD_SCHEMATICA_DEFAULTS.put("control", new int[] {Keyboard.KEY_SUBTRACT, Keyboard.KEY_M});
        OLD_SCHEMATICA_DEFAULTS.put("execute", new int[] {Keyboard.KEY_RETURN});
    }

    /** Key names of malilib config files that LWJGL does not know (as in malilib's own Keys). */
    private static final Map<String, Integer> KEY_NAMES = new HashMap<>();
    static {
        name(Keyboard.KEY_LMENU, "L_ALT", "LMENU", "L_MENU", "LALT", "LEFT_ALT");
        name(Keyboard.KEY_RMENU, "R_ALT", "RMENU", "R_MENU", "RALT", "RIGHT_ALT");
        name(Keyboard.KEY_LSHIFT, "L_SHIFT", "LSHIFT", "LEFT_SHIFT");
        name(Keyboard.KEY_RSHIFT, "R_SHIFT", "RSHIFT", "RIGHT_SHIFT");
        name(Keyboard.KEY_LCONTROL, "L_CTRL", "LCTRL", "LEFT_CTRL", "LCONTROL", "L_CONTROL", "LEFT_CONTROL");
        name(Keyboard.KEY_RCONTROL, "R_CTRL", "RCTRL", "RIGHT_CTRL", "RCONTROL", "R_CONTROL", "RIGHT_CONTROL");
        name(Keyboard.KEY_PRIOR, "PAGE_UP");
        name(Keyboard.KEY_NEXT, "PAGE_DOWN");
        name(-100, "MOUSE_LEFT", "LEFT_MOUSE", "MOUSE0", "BUTTON0", "LMB");
        name(-99, "MOUSE_RIGHT", "RIGHT_MOUSE", "MOUSE1", "BUTTON1", "RMB");
        name(-98, "MOUSE_MIDDLE", "MIDDLE_MOUSE", "MOUSE2", "BUTTON2", "MMB");
        name(-199, "SCROLL_UP");
        name(-201, "SCROLL_DOWN");
    }

    private ImportRules() {}

    private static void pairs(Map<String, String> map, String... pairs) {
        for (int i = 0; i < pairs.length; i += 2) map.put(pairs[i], pairs[i + 1]);
    }

    private static void name(int code, String... names) {
        for (String name : names) KEY_NAMES.put(name, code);
    }

    /** Our hotkey name for an old one. */
    public static String hotkey(String name) {
        if (KEYS.containsKey(name)) return KEYS.get(name);
        String setting = SETTINGS.get(name);
        return setting != null && !setting.contains(":") ? setting : name;
    }

    /** Our option name (and part, after a ':') for an old one. */
    public static String setting(String name) {
        return SETTINGS.containsKey(name) ? SETTINGS.get(name) : name;
    }

    /** Whether a key code is one our hotkeys can hold: a keyboard key, a mouse button, a scroll direction or a typed character. */
    public static boolean validCode(int code) {
        return code > 0 && code < 256 + 65536 || code >= -100 && code <= -85 || code == -199 || code == -201;
    }

    /**
     * The key codes of a malilib key list ("L_CTRL,M", "LMENU,MOUSE0", "CHAR_35" ...), or null when one key is unknown
     * (the hotkey then keeps its own keys). An empty list means "unbound".
     */
    public static List<Integer> parseKeys(String keys) {
        List<Integer> codes = new ArrayList<>();
        if (keys == null) return null;
        for (String part : keys.split(",")) {
            String name = part.trim().toUpperCase(Locale.ROOT);
            if (name.isEmpty()) continue;
            Integer code = KEY_NAMES.get(name);
            try {
                if (code == null && name.matches("BUTTON[0-9]{1,2}")) code = Integer.parseInt(name.substring(6)) - 100;
                if (code == null && name.matches("CHAR_[0-9]{1,5}")) code = Integer.parseInt(name.substring(5)) + 256;
            } catch (NumberFormatException error) {
                return null;
            }
            if (code == null) {
                int index = Keyboard.getKeyIndex(name);
                if (index != Keyboard.KEY_NONE) code = index;
            }
            if (code == null || !validCode(code)) return null;
            if (!codes.contains(code)) codes.add(code);
        }
        return codes;
    }
}
