package com.useleess.litematicaupgraded;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * Every translation key the code uses literally must exist in the en_us.lang of its domain (lang/), and every one of the
 * 13 languages (project rule: masa's 12 + German, filled by discord-bot/translate.py at release time) has every English
 * key with the same %-placeholders, \n and colour codes - a missing or broken line fails the build.
 */
public class LangKeysTest {
    private static final String[] DOMAINS = {"litematica", "malilib", "litematica_upgraded"};
    private static final String[] LANGUAGES = {"zh_cn", "zh_tw", "ru_ru", "uk_ua", "es_es", "fr_fr", "it_it", "pt_br", "ja_jp",
            "ko_kr", "sv_se", "tr_tr", "de_de"};
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[-#+ 0,(]*\\d*(?:\\.\\d+)?[sdfxXcbeEgGhHno%]|§[0-9a-fk-oqA-FK-OQ]|\\\\n|\\{[A-Za-z0-9_]*\\}");
    private static final File SOURCE = new File("src/main/java");
    private static final Pattern KEY = Pattern.compile("\"((litematica_upgraded|litematica|malilib)\\.[A-Za-z0-9_.]*[A-Za-z0-9_])\"");

    @Test
    public void everyLiteralKeyIsInTheLangFile() throws IOException {
        Set<String> lang = new HashSet<>();
        for (String domain : DOMAINS) lang.addAll(read(domain, "en_us").keySet());
        List<String> missing = new ArrayList<>();
        for (File file : javaFiles(SOURCE)) {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                Matcher m = KEY.matcher(lines.get(i));
                while (m.find()) {
                    String key = m.group(1);
                    if (!lang.contains(key)) missing.add(file.getPath() + ":" + (i + 1) + " " + key);
                }
            }
        }
        assertTrue("keys used in code but missing from en_us.lang:\n" + String.join("\n", missing), missing.isEmpty());
    }

    @Test
    public void everyLanguageHasEveryLineWithItsPlaceholders() throws IOException {
        List<String> problems = new ArrayList<>();
        for (String domain : DOMAINS) {
            Map<String, String> english = read(domain, "en_us");
            for (String language : LANGUAGES) {
                Map<String, String> lines = read(domain, language);
                for (Map.Entry<String, String> e : english.entrySet()) {
                    String have = lines.get(e.getKey());
                    if (have == null) problems.add(domain + "/" + language + ": missing " + e.getKey());
                    else if (!placeholders(have).equals(placeholders(e.getValue()))) problems.add(domain + "/" + language + ": placeholders differ in " + e.getKey());
                }
                for (String key : lines.keySet()) {
                    if (!english.containsKey(key)) problems.add(domain + "/" + language + ": not in English " + key);
                }
            }
        }
        assertTrue(problems.size() + " translation problems (run discord-bot/translate.py run):\n"
                + String.join("\n", problems.subList(0, Math.min(40, problems.size()))), problems.isEmpty());
    }

    /** The placeholders a translation must keep: every one exactly, colour codes as a set (a reset code may move). */
    private static List<String> placeholders(String text) {
        List<String> exact = new ArrayList<>();
        Set<String> colours = new java.util.TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            if (m.group().startsWith("§")) colours.add(m.group().toLowerCase());
            else exact.add(m.group());
        }
        java.util.Collections.sort(exact);
        exact.addAll(colours);
        return exact;
    }

    /** One lang file (key -> text); a language that does not exist is empty. */
    private static Map<String, String> read(String domain, String language) throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        File file = new File("src/main/resources/assets/" + domain + "/lang/" + language + ".lang");
        if (!file.exists()) return result;
        for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
            if (line.contains("=") && !line.startsWith("#")) result.put(line.substring(0, line.indexOf('=')), line.substring(line.indexOf('=') + 1));
        }
        return result;
    }

    private static List<File> javaFiles(File dir) {
        List<File> result = new ArrayList<>();
        File[] children = dir.listFiles();
        if (children == null) return result;
        for (File child : children) {
            if (child.isDirectory()) result.addAll(javaFiles(child));
            else if (child.getName().endsWith(".java")) result.add(child);
        }
        return result;
    }
}
