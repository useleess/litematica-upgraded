// Where the printer may work: Litematica's own render layer range, and the placements' boxes (after Schematica Upgraded's
// PlacementRestriction, Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.printer;

import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;
import com.useleess.litematicaupgraded.malilib.util.position.IntBoundingBox;

public final class PrinterArea {
    private PrinterArea() {}

    /** Whether the position is in the layers Litematica shows (its render layer range), when the printer is limited to them. */
    public static boolean allowed(int x, int y, int z) {
        return !PrinterConfigs.LAYER_LIMIT.getBooleanValue() || DataManager.getRenderLayerRange().isPositionWithinRange(x, y, z);
    }

    /** Whether the position is inside the box of an enabled placement: only there can the schematic call a block "extra". */
    public static boolean insidePlacement(int x, int y, int z) {
        for (SchematicPlacement placement : DataManager.getSchematicPlacementManager().getAllSchematicPlacements()) {
            if (!placement.isEnabled()) continue;
            IntBoundingBox box = placement.getEnclosingBox();
            if (box != null && x >= box.minX && x <= box.maxX && y >= box.minY && y <= box.maxY && z >= box.minZ && z <= box.maxZ) return true;
        }
        return false;
    }
}
