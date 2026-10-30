// The server options of the printer support, after Schematica Upgraded's server config (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.server;

import java.io.File;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * Read from config/litematica_upgraded-server.cfg when the game starts. Used by a dedicated server and by the integrated
 * server of a single player world. The admin command changes "printer allowed" while the server runs and saves it.
 */
public final class ServerConfig {
    public static final String CATEGORY = "server";
    /** The defaults of Schematica Upgraded. */
    public static final boolean PRINTER_ALLOWED_DEFAULT = true, ACCURATE_DEFAULT = true, AIR_DEFAULT = true, SHULKER_DEFAULT = true;
    public static final double MAX_RANGE_DEFAULT = 16, MAX_RANGE_LIMIT = 64;
    /** Server-side schematics: saving and edits (fill / replace / delete / paste / move) through the server part. */
    public static final boolean SAVING_DEFAULT = true, EDITS_DEFAULT = true;
    public static final int EDIT_LEVEL_DEFAULT = 2, UPLOAD_KIB_DEFAULT = 32 * 1024, BLOCKS_PER_TICK_DEFAULT = 20000;

    public static volatile boolean printerAllowed = PRINTER_ALLOWED_DEFAULT;
    public static volatile boolean accuratePlacement = ACCURATE_DEFAULT;
    public static volatile boolean airPlacement = AIR_DEFAULT;
    /** Farthest eye distance for printer clicks and printer mining beyond the vanilla reach; 0 keeps the vanilla reach. */
    public static volatile double maxRange = MAX_RANGE_DEFAULT;
    public static volatile boolean shulkerRestock = SHULKER_DEFAULT;
    /** Whether players may save areas through the server (full NBT, also chunks they have not loaded). */
    public static volatile boolean saving = SAVING_DEFAULT;
    /** Whether players may paste, fill, replace, delete and move through the server. */
    public static volatile boolean edits = EDITS_DEFAULT;
    /** The command permission level an editing player needs (besides creative mode). */
    public static volatile int editLevel = EDIT_LEVEL_DEFAULT;
    /** The largest edit upload (compressed), in KiB. */
    public static volatile int uploadKiB = UPLOAD_KIB_DEFAULT;
    /** How many blocks one running edit changes per server tick at most. */
    public static volatile int blocksPerTick = BLOCKS_PER_TICK_DEFAULT;

    private static Configuration configuration;

    private ServerConfig() {}

    /** Reads the options from the file (written with the defaults when it does not exist). */
    public static synchronized void load(File file) {
        configuration = new Configuration(file);
        configuration.load();
        printerAllowed = flag("printerAllowed", PRINTER_ALLOWED_DEFAULT, "Whether players may use the printer. Changed live by /litematicaupgraded printer on|off.");
        accuratePlacement = flag("accuratePlacement", ACCURATE_DEFAULT, "The printer tells the server the exact facing, half, axis, delay and mode of a block (accurate placement).");
        airPlacement = flag("airPlacement", AIR_DEFAULT, "The server places fence gates in the air for the printer (no scaffold block needed).");
        shulkerRestock = flag("shulkerRestock", SHULKER_DEFAULT, "The server moves items the printer needs out of shulker boxes in the player's inventory.");
        saving = flag("serverSideSaving", SAVING_DEFAULT, "Players may save areas through the server: the server reads the blocks with full NBT, also chunks the player has not loaded. Changed live by /litematicaupgraded saving on|off.");
        edits = flag("serverSideEdits", EDITS_DEFAULT, "Players may paste, fill, replace, delete and move through the server (full NBT, spread over server ticks). Changed live by /litematicaupgraded edits on|off.");
        editLevel = clampLevel(configuration.get(CATEGORY, "editPermissionLevel", EDIT_LEVEL_DEFAULT,
            "The command permission level (0-4) a player needs for server-side edits, besides creative mode. 2 = operators.", 0, 4).getInt(EDIT_LEVEL_DEFAULT));
        uploadKiB = clampUpload(configuration.get(CATEGORY, "maxUploadKiB", UPLOAD_KIB_DEFAULT,
            "The largest edit upload (compressed) the server accepts, in KiB.", 1, 262144).getInt(UPLOAD_KIB_DEFAULT));
        blocksPerTick = Math.max(100, Math.min(1000000, configuration.get(CATEGORY, "editBlocksPerTick", BLOCKS_PER_TICK_DEFAULT,
            "How many blocks one running edit changes per server tick at most (the edit is spread over many ticks).", 100, 1000000).getInt(BLOCKS_PER_TICK_DEFAULT)));
        Property range = configuration.get(CATEGORY, "maxRange", MAX_RANGE_DEFAULT,
            "Farthest distance (blocks, from the eyes) for printer clicks and mining beyond the vanilla reach. 0 keeps the vanilla reach.", 0.0, MAX_RANGE_LIMIT);
        maxRange = clampRange(range.getDouble(MAX_RANGE_DEFAULT));
        save();
    }

    private static boolean flag(String name, boolean fallback, String comment) {
        return configuration.get(CATEGORY, name, fallback, comment).getBoolean(fallback);
    }

    static int clampLevel(int value) {
        return Math.max(0, Math.min(4, value));
    }

    static int clampUpload(int value) {
        return Math.max(1, Math.min(262144, value));
    }

    static double clampRange(double value) {
        return Math.max(0, Math.min(MAX_RANGE_LIMIT, value));
    }

    /** Writes the current values. */
    public static synchronized void save() {
        if (configuration == null) return;
        configuration.get(CATEGORY, "printerAllowed", PRINTER_ALLOWED_DEFAULT).set(printerAllowed);
        configuration.get(CATEGORY, "accuratePlacement", ACCURATE_DEFAULT).set(accuratePlacement);
        configuration.get(CATEGORY, "airPlacement", AIR_DEFAULT).set(airPlacement);
        configuration.get(CATEGORY, "shulkerRestock", SHULKER_DEFAULT).set(shulkerRestock);
        configuration.get(CATEGORY, "maxRange", MAX_RANGE_DEFAULT).set(maxRange);
        configuration.get(CATEGORY, "serverSideSaving", SAVING_DEFAULT).set(saving);
        configuration.get(CATEGORY, "serverSideEdits", EDITS_DEFAULT).set(edits);
        configuration.get(CATEGORY, "editPermissionLevel", EDIT_LEVEL_DEFAULT).set(editLevel);
        configuration.get(CATEGORY, "maxUploadKiB", UPLOAD_KIB_DEFAULT).set(uploadKiB);
        configuration.get(CATEGORY, "editBlocksPerTick", BLOCKS_PER_TICK_DEFAULT).set(blocksPerTick);
        configuration.save();
    }

    /** The admin switch: changes "printer allowed" and saves it. */
    public static synchronized void setPrinterAllowed(boolean allowed) {
        printerAllowed = allowed;
        save();
    }

    /** The admin switches of the server-side schematics: change an option and save it. */
    public static synchronized void setSaving(boolean allowed) {
        saving = allowed;
        save();
    }

    public static synchronized void setEdits(boolean allowed) {
        edits = allowed;
        save();
    }

    public static synchronized void setEditLevel(int level) {
        editLevel = clampLevel(level);
        save();
    }

    public static synchronized void setUploadKiB(int kib) {
        uploadKiB = clampUpload(kib);
        save();
    }

    public static synchronized void setBlocksPerTick(int blocks) {
        blocksPerTick = Math.max(100, Math.min(1000000, blocks));
        save();
    }

    public static long maxUploadBytes() {
        return (long) uploadKiB * 1024;
    }
}
