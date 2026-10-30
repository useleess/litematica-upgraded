package com.useleess.litematicaupgraded;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;

/** Every translation key the code uses literally must exist in the en_us.lang of its domain (lang/). */
public class LangKeysTest {
    private static final File SOURCE = new File("src/main/java");
    private static final Pattern KEY = Pattern.compile("\"((litematica_upgraded|litematica|malilib)\\.[A-Za-z0-9_.]*[A-Za-z0-9_])\"");

    @Test
    public void everyLiteralKeyIsInTheLangFile() throws IOException {
        Set<String> lang = new HashSet<>();
        for (String domain : new String[] {"litematica", "malilib", "litematica_upgraded"}) {
            File file = new File("src/main/resources/assets/" + domain + "/lang/en_us.lang");
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (line.contains("=") && !line.startsWith("#")) lang.add(line.substring(0, line.indexOf('=')));
            }
        }
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
