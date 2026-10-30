package com.useleess.litematicaupgraded;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.Test;

/**
 * The in-world tests find buttons and screens by name, never by a fixed position or window size: a call that clicks
 * at numbers (mouseClicked / clickAt with literal coordinates) fails the build. Project rule, never weakened.
 */
public class SelfTestHygieneTest {
    private static final File SELFTEST = new File("src/selftest/java");
    private static final Pattern FIXED_CLICK = Pattern.compile("\\b(mouseClicked|clickAt|click)\\s*\\([^)]*\\b\\d+\\b");

    @Test
    public void noFixedPositionClicks() throws IOException {
        List<String> problems = new ArrayList<>();
        for (File file : javaFiles(SELFTEST)) {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                if (FIXED_CLICK.matcher(lines.get(i)).find()) {
                    problems.add(file.getPath() + ":" + (i + 1) + " clicks at fixed numbers: " + lines.get(i).trim());
                }
            }
        }
        assertTrue(String.join("\n", problems), problems.isEmpty());
    }

    private static List<File> javaFiles(File dir) {
        List<File> out = new ArrayList<>();
        File[] children = dir.listFiles();
        if (children == null) return out;
        for (File child : children) {
            if (child.isDirectory()) out.addAll(javaFiles(child));
            else if (child.getName().endsWith(".java")) out.add(child);
        }
        return out;
    }
}
