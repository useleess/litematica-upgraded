package com.useleess.litematicaupgraded;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * No copied code in our own sources (src/main/java/com/useleess/litematicaupgraded, without the litematica/ and
 * malilib/ packages, which are masa's code converted by tools/convert_upstream.py): 6 or more non-trivial lines in a
 * row may not appear twice (comments, blank lines, lone braces and imports don't count), and an array constant of 4
 * or more items may not be written out twice. Copies go into one shared method or table instead (see CODE_MAP.md).
 * Project rule ("every mod from day one", point 1): never weakened, no exception list.
 */
public class CodeHygieneTest {
    /** Lines in a row that may not appear twice. */
    private static final int COPY_LINES = 6;
    /** Items an array constant needs to count as a table. */
    private static final int TABLE_ITEMS = 4;
    private static final Path OURS = Paths.get("src/main/java/com/useleess/litematicaupgraded");
    private static final Pattern UPSTREAM = Pattern.compile("^(litematica|malilib)/");

    private static final Map<String, String> SOURCES = new TreeMap<>();

    @BeforeClass
    public static void read() throws IOException {
        try (Stream<Path> files = Files.walk(OURS)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList())) {
                String name = OURS.relativize(file).toString().replace('\\', '/');
                if (UPSTREAM.matcher(name).find()) continue;
                SOURCES.put(name, new String(Files.readAllBytes(file), StandardCharsets.UTF_8));
            }
        }
        assertTrue("our sources not found", SOURCES.size() > 50);
    }

    /** The code without comments, as trimmed lines that say something. */
    private static List<String> codeLines(String source) {
        String noComments = source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\\n]*", "");
        List<String> out = new ArrayList<>();
        for (String line : noComments.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.matches("[{}();,]*") || t.matches("this\\.(\\w+) = \\1;") || t.startsWith("import ")
                || t.startsWith("package ") || t.startsWith("@") || t.equals("break;") || t.equals("return;")
                || t.equals("} else {") || t.equals("try {") || t.startsWith("} catch")) continue;
            out.add(t);
        }
        return out;
    }

    @Test
    public void noCopiedCode() {
        Map<String, String> seen = new HashMap<>();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> file : SOURCES.entrySet()) {
            List<String> lines = codeLines(file.getValue());
            for (int i = 0; i + COPY_LINES <= lines.size(); i++) {
                String block = String.join("\n", lines.subList(i, i + COPY_LINES));
                String where = file.getKey() + " (code line " + (i + 1) + ")";
                String other = seen.putIfAbsent(block, where);
                if (other != null) {
                    problems.add(where + " repeats " + other + ":\n" + block);
                    break;
                }
            }
        }
        assertTrue("Copied code - make one shared method instead (see CODE_MAP.md):\n\n" + String.join("\n\n", problems),
            problems.isEmpty());
    }

    @Test
    public void noRepeatedTables() {
        Pattern table = Pattern.compile("=\\s*(?:new [\\w\\[\\]]+\\s*)?\\{([^{}]{20,})\\}\\s*;");
        Map<String, String> seen = new HashMap<>();
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> file : SOURCES.entrySet()) {
            Matcher m = table.matcher(file.getValue());
            while (m.find()) {
                String items = m.group(1).replaceAll("\\s+", "");
                if (items.split(",").length < TABLE_ITEMS) continue;
                String other = seen.putIfAbsent(items, file.getKey());
                if (other != null) problems.add(file.getKey() + " and " + other + ": {" + items + "}");
            }
        }
        assertTrue("The same table twice - keep one and refer to it:\n" + String.join("\n", problems), problems.isEmpty());
    }
}
