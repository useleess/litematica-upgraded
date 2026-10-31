package com.useleess.litematicaupgraded;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Project rule: credits + licences for everything taken from others, in the README, the mod info (Forge's mod list) and
 * the settings (the Credits entry of the Litematica Upgraded tab). Every place names every one of them.
 */
public class CreditsTest {
    /** Who must be named, and how the text names them. */
    private static final String[] NAMES = {"masa", "LGPL-3.0", "Schematica", "Lunatrius", "HackerRouter", "MIT", "translators"};

    @Test
    public void everyPlaceNamesEveryone() throws IOException {
        List<String> missing = new ArrayList<>();
        check("README.md", read("README.md"), missing);
        check("mcmod.info", read("src/main/resources/mcmod.info"), missing);
        String settings = "";
        for (String line : Files.readAllLines(Paths.get("src/main/resources/assets/litematica_upgraded/lang/en_us.lang"), StandardCharsets.UTF_8)) {
            if (line.startsWith("litematica_upgraded.config.comment.credits=")) settings = line;
        }
        check("the Credits setting (en_us.lang)", settings, missing);
        assertTrue("credits missing:\n" + String.join("\n", missing), missing.isEmpty());
    }

    private static void check(String place, String text, List<String> missing) {
        for (String name : NAMES) {
            if (!text.contains(name)) missing.add(place + " does not name " + name);
        }
    }

    private static String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
