package com.useleess.litematicaupgraded.mixin;

import java.nio.file.Path;
import java.util.Optional;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.useleess.litematicaupgraded.litematica.Litematica;
import com.useleess.litematicaupgraded.litematica.schematic.LoadedSchematic;
import com.useleess.litematicaupgraded.litematica.schematic.Schematic;
import com.useleess.litematicaupgraded.litematica.schematic.SchematicType;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageDispatcher;
import com.useleess.litematicaupgraded.malilib.util.data.tag.CompoundData;
import com.useleess.litematicaupgraded.malilib.util.data.tag.DataView;
import com.useleess.litematicaupgraded.malilib.util.data.tag.util.DataFileUtils;
import com.useleess.litematicaupgraded.perf.MemoryBudget;
import com.useleess.litematicaupgraded.perf.PerfConfigs;

/**
 * A file that will not fit in the game's memory gets a red message instead of a crash: refused up front when its unpacked
 * size is over the read budget, and an out-of-memory while reading or building it is caught the same way.
 */
@Mixin(value = LoadedSchematic.class, remap = false)
public abstract class MixinPerfLoadedSchematic
{
    @Inject(method = "tryLoadSchematic", at = @At("HEAD"), cancellable = true)
    private static void perf$checkBudget(Path schematicFile, CallbackInfoReturnable<Optional<LoadedSchematic>> cir)
    {
        if (PerfConfigs.SPARSE_STORAGE.getBooleanValue() == false)
        {
            return;
        }

        long unpacked = MemoryBudget.gzipUnpackedSize(schematicFile);

        if (unpacked > 0 && MemoryBudget.fits(unpacked) == false)
        {
            perf$tooBig(schematicFile, unpacked);
            cir.setReturnValue(Optional.empty());
        }
    }

    @Redirect(method = "tryLoadSchematic", at = @At(value = "INVOKE", target = "Lcom/useleess/litematicaupgraded/malilib/util/data/tag/util/DataFileUtils;readCompoundDataFromNbtFile(Ljava/nio/file/Path;)Lcom/useleess/litematicaupgraded/malilib/util/data/tag/CompoundData;"))
    private static CompoundData perf$read(Path file)
    {
        try
        {
            return DataFileUtils.readCompoundDataFromNbtFile(file);
        }
        catch (OutOfMemoryError e)
        {
            perf$tooBig(file, -1);
            return null;
        }
    }

    @Redirect(method = "tryLoadSchematic", at = @At(value = "INVOKE", target = "Lcom/useleess/litematicaupgraded/litematica/schematic/SchematicType;createSchematicFromData(Lcom/useleess/litematicaupgraded/malilib/util/data/tag/DataView;)Ljava/util/Optional;"))
    private static Optional<Schematic> perf$create(SchematicType type, DataView data)
    {
        try
        {
            return type.createSchematicFromData(data);
        }
        catch (OutOfMemoryError e)
        {
            perf$tooBig(null, -1);
            return Optional.empty();
        }
    }

    private static void perf$tooBig(Path file, long unpackedBytes)
    {
        MemoryBudget.countRefusal();
        long maxMb = MemoryBudget.maxMemory() / (1024 * 1024);
        Litematica.LOGGER.warn("Not enough memory to load {} (unpacked {} bytes, the game has {} MB)", file, unpackedBytes, maxMb);
        MessageDispatcher.error(MemoryBudget.OUT_OF_MEMORY_KEY, maxMb);
    }
}
