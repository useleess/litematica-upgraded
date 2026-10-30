package com.useleess.litematicaupgraded.convert.gui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.useleess.litematicaupgraded.convert.FileConverter;
import com.useleess.litematicaupgraded.convert.ModernFiles;
import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageDispatcher;
import com.useleess.litematicaupgraded.malilib.util.data.tag.util.DataFileUtils;

/**
 * Opens the missing-blocks window when the player loads a file with blocks 1.12 does not have (the schematic browser's
 * load, reload): never when a world is joined and placements come back. The window is the only "answer": converting
 * writes the file over itself, so a file with no missing blocks left never opens it again.
 */
public final class MissingBlocksPrompt
{
    private static final Set<Path> SKIP_ONCE = new HashSet<>();

    private MissingBlocksPrompt() {}

    /** The blocks of the file that 1.12 does not have, by name with counts; empty for any other file. */
    public static Map<String, Long> missingBlocksOf(Path file)
    {
        if (Files.isRegularFile(file) == false)
        {
            return Collections.emptyMap();
        }

        DataFileUtils.readCompoundDataFromNbtFile(file);
        return ModernFiles.lastWasModern() ? ModernFiles.lastMissing() : Collections.emptyMap();
    }

    /** The next {@link #open} for this file does nothing (the player chose to load it as it is). */
    public static void skipOnce(Path file)
    {
        SKIP_ONCE.add(file.toAbsolutePath().normalize());
    }

    /**
     * Opens the window when the file is a newer-version file with missing blocks.
     * @param afterwards gets the path of the file to load: the converted one after "Use these", else the file itself
     * @return true when the window opened (the caller must not load the file itself then)
     */
    public static boolean open(Path file, Consumer<Path> afterwards)
    {
        if (SKIP_ONCE.remove(file.toAbsolutePath().normalize()))
        {
            return false;
        }

        Map<String, Long> missing = missingBlocksOf(file);

        if (missing.isEmpty())
        {
            return false;
        }

        MissingBlocksScreen screen = new MissingBlocksScreen(file, missing, s -> convert(file, afterwards), () -> afterwards.accept(file));
        BaseScreen.openScreenWithParent(screen);
        return true;
    }

    private static void convert(Path file, Consumer<Path> afterwards)
    {
        try
        {
            Path converted = FileConverter.convertOnDisk(file);
            MessageDispatcher.success("litematica_upgraded.message.missing.converted",
                                      file.getFileName().toString(), converted.getFileName().toString());
            afterwards.accept(converted);
        }
        catch (IOException | RuntimeException e)
        {
            MessageDispatcher.error().console(e).translate("litematica_upgraded.message.missing.convert_failed",
                                                           file.getFileName().toString());
            skipOnce(file);
            afterwards.accept(file);
        }
    }
}
