package com.useleess.litematicaupgraded.importer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.google.gson.JsonPrimitive;

/**
 * The one-time copy of keybinds and settings from the mods players come from, without the game: the values read from
 * the old files are merged (the most recently changed file wins), checked and handed to a {@link Target}.
 */
public final class ImportCore {
    /** One value read from an old file, already under our name. */
    public static final class Entry {
        /** For a setting: "" for its main value, or one part of a two-value option ("d", "i", "x", "y", "dir"). */
        public final String name, part, source;
        /** A setting's value, or null for a hotkey. */
        public final JsonPrimitive value;
        /** A hotkey's keys (empty = unbound), or null for a setting. */
        public final List<Integer> keys;
        /** When the file it came from was last changed. */
        public final long time;

        private Entry(String name, String part, JsonPrimitive value, List<Integer> keys, String source, long time) {
            this.name = name;
            this.part = part;
            this.value = value;
            this.keys = keys;
            this.source = source;
            this.time = time;
        }

        public static Entry setting(String name, String part, JsonPrimitive value, String source, long time) {
            return new Entry(name, part, value, null, source, time);
        }

        public static Entry hotkey(String name, List<Integer> keys, String source, long time) {
            return new Entry(name, "", null, Collections.unmodifiableList(new ArrayList<>(keys)), source, time);
        }

        boolean isKey() {
            return keys != null;
        }

        String id() {
            return (isKey() ? "key " : "setting ") + name + (part.isEmpty() ? "" : ":" + part);
        }
    }

    /** What happened to one value given to the target. */
    public enum Outcome { APPLIED, SAME, BAD, UNKNOWN }

    /** Our options and hotkeys, as the copy sees them. */
    public interface Target {
        boolean hasSetting(String name);

        /** Whether the player already changed it from its default (then it is never overwritten). */
        boolean settingModified(String name);

        /** Sets the value when it fits the option's type and limits. */
        Outcome setSetting(String name, String part, JsonPrimitive value);

        /** Every hotkey, in the order clashes are settled (the first keeps its keys). */
        List<String> keyNames();

        boolean keyModified(String name);

        List<Integer> keys(String name);

        void setKeys(String name, List<Integer> keys);
    }

    /** What the copy did. */
    public static final class Result {
        public int keys, settings;
        /** The sources at least one copied value came from, in the order found. */
        public final Set<String> sources = new LinkedHashSet<>();
        public final List<String> skipped = new ArrayList<>();
    }

    private ImportCore() {}

    /** For every option / hotkey (and part) the value from the most recently changed file; the first found on a tie. */
    public static List<Entry> newestWins(List<Entry> entries) {
        Map<String, Entry> winners = new LinkedHashMap<>();
        for (Entry entry : entries) {
            Entry before = winners.get(entry.id());
            if (before == null || entry.time > before.time) winners.put(entry.id(), entry);
        }
        return new ArrayList<>(winners.values());
    }

    /** Copies what fits into the target; never touches what the player changed; logs every value it skips. */
    public static Result apply(List<Entry> entries, Target target, Consumer<String> log) {
        Result result = new Result();
        List<Entry> winners = newestWins(entries);
        // settings: whether the player changed one is looked at before anything is set (a two-part option is set part by part)
        Map<String, Boolean> changedByPlayer = new HashMap<>();
        Set<String> copied = new HashSet<>();
        for (Entry entry : winners) {
            if (entry.isKey()) continue;
            String name = entry.name, part = entry.part;
            // older files kept the two numbers of a position as nameX / nameY
            if (!target.hasSetting(name) && part.isEmpty() && name.length() > 1 && (name.endsWith("X") || name.endsWith("Y"))
                    && target.hasSetting(name.substring(0, name.length() - 1))) {
                part = name.endsWith("X") ? "x" : "y";
                name = name.substring(0, name.length() - 1);
            }
            if (!target.hasSetting(name)) continue;
            if (changedByPlayer.computeIfAbsent(name, target::settingModified)) {
                skip(result, log, "setting " + name + " from " + entry.source + ": already changed in Litematica Upgraded");
                continue;
            }
            Outcome outcome;
            try {
                outcome = target.setSetting(name, part, entry.value);
            } catch (RuntimeException error) {
                outcome = Outcome.BAD;
            }
            if (outcome == Outcome.BAD) skip(result, log, "setting " + name + (part.isEmpty() ? "" : ":" + part) + " = " + entry.value + " from " + entry.source + ": does not fit the option");
            if (outcome == Outcome.APPLIED) {
                if (copied.add(name)) result.settings++;
                result.sources.add(entry.source);
            }
        }
        // hotkeys: only those still at their default and different from what they have now
        Map<String, Entry> wanted = new LinkedHashMap<>();
        for (Entry entry : winners) {
            if (!entry.isKey() || !target.keyNames().contains(entry.name)) continue;
            if (target.keyModified(entry.name)) {
                skip(result, log, "keybind " + entry.name + " from " + entry.source + ": already changed in Litematica Upgraded");
                continue;
            }
            if (!sameKeys(entry.keys, target.keys(entry.name))) wanted.put(entry.name, entry);
        }
        // no two actions on one key: where two end up on the same keys, a copied one gives way (the later of two
        // copied ones, so the first keeps them); repeated, as a copy given up brings its old keys back
        List<String> order = target.keyNames();
        boolean changed = true;
        while (changed) {
            changed = false;
            Map<Set<Integer>, String> seen = new HashMap<>();
            for (String name : order) {
                Entry entry = wanted.get(name);
                Set<Integer> keys = new HashSet<>(entry != null ? entry.keys : target.keys(name));
                if (keys.isEmpty()) continue;
                String first = seen.putIfAbsent(keys, name);
                if (first == null) continue;
                String loser = entry != null ? name : wanted.containsKey(first) ? first : null;
                if (loser == null) continue; // two of ours that already share keys: not the copy's business
                skip(result, log, "keybind " + loser + " from " + wanted.get(loser).source + ": its keys would also start another action");
                wanted.remove(loser);
                changed = true;
                break;
            }
        }
        for (Entry entry : wanted.values()) {
            target.setKeys(entry.name, entry.keys);
            result.keys++;
            result.sources.add(entry.source);
        }
        return result;
    }

    private static boolean sameKeys(List<Integer> a, List<Integer> b) {
        return a.size() == b.size() && new HashSet<>(a).equals(new HashSet<>(b));
    }

    private static void skip(Result result, Consumer<String> log, String why) {
        result.skipped.add(why);
        log.accept("Not copied: " + why);
    }
}
