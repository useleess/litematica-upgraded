package com.useleess.litematicaupgraded.convert;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.google.common.collect.ImmutableMap;

import com.useleess.litematicaupgraded.litematica.schematic.Schematic;
import com.useleess.litematicaupgraded.litematica.schematic.SchematicMetadata;
import com.useleess.litematicaupgraded.litematica.schematic.SchematicRegion;
import com.useleess.litematicaupgraded.litematica.schematic.SchematicType;
import com.useleess.litematicaupgraded.litematica.schematic.container.ArrayBlockContainer;
import com.useleess.litematicaupgraded.litematica.schematic.container.BlockContainer;
import com.useleess.litematicaupgraded.litematica.schematic.container.TightLongBackedIntArray;
import com.useleess.litematicaupgraded.litematica.schematic.util.SchematicFileUtils;
import com.useleess.litematicaupgraded.malilib.util.data.tag.CompoundData;
import com.useleess.litematicaupgraded.malilib.util.data.tag.util.DataFileUtils;
import com.useleess.litematicaupgraded.malilib.util.position.Vec3i;
import com.useleess.litematicaupgraded.malilib.util.world.BlockState;

/**
 * Writes a schematic file of a newer Minecraft version over itself as a 1.12 file (no backup: that is the player's
 * choice in the missing-blocks window). A .litematic stays the same file; a Sponge .schem or vanilla .nbt becomes the
 * same-name .litematic and the original is deleted, so the converted file on disk is the only truth.
 */
public final class FileConverter {
    private FileConverter() {}

    /** The file that holds the converted schematic (the same path for a .litematic). */
    public static Path convertOnDisk(Path file) throws IOException {
        CompoundData data = DataFileUtils.readCompoundDataFromNbtFile(file);
        if (data == null || !ModernFiles.lastWasModern()) throw new IOException("not a schematic of a newer version: " + file);
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String base = dot < 0 ? name : name.substring(0, dot);
        if (name.toLowerCase(Locale.ROOT).endsWith(".litematic")) {
            Path temp = file.resolveSibling(name + ".converting");
            try {
                if (!DataFileUtils.writeCompoundDataToCompressedNbtFile(temp, data, "")) throw new IOException("could not write " + temp);
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(temp);
            }
            return file;
        }
        Optional<SchematicType> type = SchematicType.getTypeFromData(file, data);
        Optional<Schematic> read = type.isPresent() ? type.get().createSchematicFromData(data) : Optional.empty();
        if (!read.isPresent()) throw new IOException("could not read " + file);
        Optional<Schematic> converted = SchematicType.LITEMATICA.createSchematicFromRegions(tightRegions(read.get().getRegions()));
        if (!converted.isPresent()) throw new IOException("could not convert " + file);
        SchematicMetadata metadata = converted.get().getMetadata();
        metadata.copyFrom(read.get().getMetadata());
        metadata.setTimeModifiedToNow();
        if (metadata.getSchematicName().isEmpty()) metadata.setSchematicName(base);
        Path target = file.resolveSibling(base + ".litematic");
        for (int i = 1; Files.exists(target); i++) target = file.resolveSibling(base + "_" + i + ".litematic");
        if (!SchematicFileUtils.writeToFile(converted.get(), target, false)) throw new IOException("could not write " + target);
        Files.delete(file);
        return target;
    }

    /**
     * The regions with their blocks in a tightly packed container of their own. Litematica's .litematic writer pairs
     * the palette of a container with the packed array of a temporary copy, whose palette starts with air: for a
     * container read from a Sponge or structure file that shifts every block by one id.
     */
    private static ImmutableMap<String, SchematicRegion> tightRegions(ImmutableMap<String, SchematicRegion> regions) {
        ImmutableMap.Builder<String, SchematicRegion> builder = ImmutableMap.builder();
        for (Map.Entry<String, SchematicRegion> entry : regions.entrySet()) {
            SchematicRegion region = entry.getValue();
            BlockContainer source = region.getBlockContainer();
            Vec3i size = source.getSize();
            List<BlockState> palette = new ArrayList<>();
            Map<BlockState, Integer> ids = new HashMap<>();
            palette.add(BlockState.AIR);
            ids.put(BlockState.AIR, 0);
            for (int i = 0; i < source.getPalette().getSize(); i++) {
                BlockState state = source.getPalette().getValue(i);
                if (state != null && !ids.containsKey(state)) {
                    ids.put(state, palette.size());
                    palette.add(state);
                }
            }
            long volume = (long) size.getX() * size.getY() * size.getZ();
            TightLongBackedIntArray storage = new TightLongBackedIntArray(ArrayBlockContainer.getRequiredBitWidth(palette.size()), volume);
            for (int y = 0; y < size.getY(); y++) {
                for (int z = 0; z < size.getZ(); z++) {
                    for (int x = 0; x < size.getX(); x++) {
                        BlockState state = source.getBlockState(x, y, z);
                        Integer id = ids.get(state);
                        storage.setAt(((long) y * size.getZ() + z) * size.getX() + x, id == null ? 0 : id);
                    }
                }
            }
            ArrayBlockContainer container = new ArrayBlockContainer(size, storage);
            container.getPalette().setMapping(palette);
            builder.put(entry.getKey(), new SchematicRegion(region.getRelativePosition(), region.getSize(), container, region.getBlockEntityMap(),
                                                            region.getBlockTickMap(), region.getEntityList(), region.getMinecraftDataVersion()));
        }
        return builder.build();
    }
}
