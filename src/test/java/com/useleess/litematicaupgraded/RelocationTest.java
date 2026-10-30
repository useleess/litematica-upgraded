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

/** The upstream packages must all be relocated, and no Fabric / Ornithe / OSL import may be left. */
public class RelocationTest {
    private static final File SOURCE = new File("src/main/java");
    private static final Pattern UNRELOCATED = Pattern.compile("^\\s*import\\s+(static\\s+)?(litematica|malilib)\\.");
    private static final Pattern LOADER_API = Pattern.compile("^\\s*import\\s+(static\\s+)?net\\.(fabricmc|ornithemc)\\.");

    @Test
    public void upstreamPackagesAreRelocated() throws IOException {
        List<String> problems = new ArrayList<>();
        for (File file : javaFiles(SOURCE)) {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);
                String where = file.getPath() + ":" + (i + 1);
                if (UNRELOCATED.matcher(line).find()) {
                    problems.add(where + " not relocated: " + line.trim());
                }
                if (LOADER_API.matcher(line).find()) {
                    problems.add(where + " loader API left in: " + line.trim());
                }
            }
        }
        assertTrue("found " + problems.size() + " problems:\n" + String.join("\n", problems), problems.isEmpty());
    }

    private static List<File> javaFiles(File dir) {
        List<File> result = new ArrayList<>();
        File[] children = dir.listFiles();
        if (children == null) {
            return result;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                result.addAll(javaFiles(child));
            } else if (child.getName().endsWith(".java")) {
                result.add(child);
            }
        }
        return result;
    }
}
