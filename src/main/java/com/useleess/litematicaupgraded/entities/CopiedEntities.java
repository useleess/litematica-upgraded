package com.useleess.litematicaupgraded.entities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.useleess.litematicaupgraded.litematica.schematic.LoadedSchematic;
import com.useleess.litematicaupgraded.litematica.schematic.SchematicRegion;
import com.useleess.litematicaupgraded.litematica.schematic.data.EntityData;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageDispatcher;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageOutput;
import com.useleess.litematicaupgraded.malilib.util.data.tag.CompoundData;

/**
 * Spots schematics saved after a paste with the copy bug of masa's original 1.12 Litematica, which spawned every entity
 * once per chunk the paste covered. Its mark: mobs saved as perfect twins (all data equal but the id), every twinned mob
 * the same number of times. Real mobs are never perfect twins (health, age, trades, pushing apart), so a trading hall or
 * a mob farm is never taken for it; armor stands and minecarts can be real twins, so they never count as evidence.
 */
public final class CopiedEntities
{
    /** Only mobs carry this tag (armor stands, minecarts and items do not). */
    private static final String MOB_TAG = "CanPickUpLoot";

    private CopiedEntities()
    {
    }

    /** {real mobs, times each was saved} when the bug left its mark, else null. */
    static int[] find(List<CompoundData> entities)
    {
        Map<CompoundData, Integer> twins = new HashMap<>();

        for (CompoundData tag : entities)
        {
            if (tag.getKeys().contains(MOB_TAG))
            {
                CompoundData key = tag.copy();
                key.remove("UUIDMost");
                key.remove("UUIDLeast");
                twins.merge(key, 1, Integer::sum);
            }
        }

        int times = 0;
        int mobs = 0;

        for (int count : twins.values())
        {
            if (count < 2)
            {
                continue;
            }

            if (times != 0 && count != times)
            {
                return null;
            }

            times = count;
            mobs++;
        }

        return times == 0 ? null : new int[] {mobs, times};
    }

    /** Tells the player in chat when a schematic they load carries the bug's copies. */
    public static void warnIfCopied(LoadedSchematic schematic)
    {
        List<CompoundData> entities = new ArrayList<>();

        for (SchematicRegion region : schematic.schematic.getRegions().values())
        {
            List<EntityData> list = region.getEntityList();

            if (list != null)
            {
                list.forEach(entity -> entities.add(entity.data));
            }
        }

        int[] found = find(entities);

        if (found != null)
        {
            MessageDispatcher.warning().type(MessageOutput.CHAT).console().translate("litematica_upgraded.message.copied_entities",
                    schematic.schematic.getMetadata().getSchematicName(), found[0] * found[1], found[0], found[1]);
        }
    }
}
