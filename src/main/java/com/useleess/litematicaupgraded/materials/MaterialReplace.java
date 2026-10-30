package com.useleess.litematicaupgraded.materials;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import com.useleess.litematicaupgraded.gui.BlockPicker;
import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.materials.MaterialCache;
import com.useleess.litematicaupgraded.litematica.materials.MaterialListBase;
import com.useleess.litematicaupgraded.litematica.materials.MaterialListEntry;
import com.useleess.litematicaupgraded.litematica.materials.MaterialListPlacement;
import com.useleess.litematicaupgraded.litematica.schematic.LoadedSchematic;
import com.useleess.litematicaupgraded.litematica.schematic.SchematicRegion;
import com.useleess.litematicaupgraded.litematica.schematic.container.BlockContainer;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacementManager;
import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.malilib.gui.util.GuiUtils;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageDispatcher;
import com.useleess.litematicaupgraded.malilib.util.data.palette.Palette;
import com.useleess.litematicaupgraded.malilib.util.position.Vec3i;
import com.useleess.litematicaupgraded.malilib.util.world.BlockState;
import com.useleess.litematicaupgraded.mixin.IMixinMaterialListPlacement;

/**
 * The material list's Replace: every block of a row's item in the placement's loaded schematic becomes another block,
 * keeping the properties both share (ReplaceRules). Only the schematic in memory changes (marked as modified, so the
 * player can save it); the schematic world is rebuilt, so ghost blocks, verifier and printer follow, and the material
 * list counts again once the rebuild is through. Replacing back restores it.
 */
public final class MaterialReplace {
    public static final MaterialReplace INSTANCE = new MaterialReplace();
    /** Ticks a material list waits for the schematic world's rebuild before it counts again anyway. */
    private static final int REFRESH_TIMEOUT = 100;

    private final List<MaterialListBase> toRefresh = new ArrayList<>();
    private int waited;

    private MaterialReplace() {}

    /** Whether the list belongs to a placement (the lists Replace works on). */
    public static boolean supports(MaterialListBase list) {
        return list instanceof MaterialListPlacement;
    }

    /** Opens the block picker for a row; the picked block replaces the row's blocks in the list's placement. */
    public static void openPicker(MaterialListBase list, MaterialListEntry entry) {
        BaseScreen parent = GuiUtils.getCurrentScreen() instanceof BaseScreen ? (BaseScreen) GuiUtils.getCurrentScreen() : null;
        BlockPicker.open(parent, false, "litematica_upgraded.title.screen.material.replace", entry.getStack().getDisplayName(),
                         block -> replace(list, entry.getStack(), block));
    }

    /** Replaces the item's blocks in the list's placement and has the list count again; the number of blocks changed. */
    public static long replace(MaterialListBase list, ItemStack item, Block to) {
        SchematicPlacement placement = ((IMixinMaterialListPlacement) list).litematicaUpgradedPlacement();
        long changed = replace(placement, item, to);
        ItemStack toItem = new ItemStack(to);
        String name = toItem.isEmpty() ? to.getLocalizedName() : toItem.getDisplayName();
        if (changed > 0) {
            MessageDispatcher.success(3000).translate("litematica_upgraded.message.material.replaced", changed, item.getDisplayName(), name);
            INSTANCE.refreshLater(list);
        } else {
            MessageDispatcher.warning(3000).translate("litematica_upgraded.message.material.replace_none", item.getDisplayName());
        }
        return changed;
    }

    /** Every block of the placement's schematic whose build item is the given item becomes the new block; the number changed. */
    public static long replace(SchematicPlacement placement, ItemStack item, Block to) {
        LoadedSchematic loaded = placement.getLoadedSchematic();
        if (loaded == null || item.isEmpty()) return 0;
        long changed = 0;
        for (SchematicRegion region : loaded.schematic.getRegions().values()) {
            BlockContainer container = region.getBlockContainer();
            if (container != null) changed += replace(container, item, to);
        }
        if (changed > 0) {
            loaded.setModifiedSinceSaved();
            DataManager.getSchematicPlacementManager().markAllPlacementsOfSchematicForRebuild(loaded);
        }
        return changed;
    }

    /** Swaps the matching states of one block container: in its palette when that keeps one entry per state, else block by block. */
    private static long replace(BlockContainer container, ItemStack item, Block to) {
        Palette<BlockState> palette = container.getPalette();
        List<BlockState> mapping = palette.getMapping();
        Map<BlockState, BlockState> swaps = new HashMap<>();
        for (BlockState state : mapping) {
            if (state == null || state.vanillaState().getBlock() == to) continue;
            ItemStack needed = MaterialCache.getInstance().getRequiredBuildItemForState(state);
            if (needed == null || needed.isEmpty() || !ItemStack.areItemsEqual(needed, item)) continue;
            swaps.put(state, BlockState.of(ReplaceRules.withSharedProperties(state.vanillaState(), to)));
        }
        if (swaps.isEmpty()) return 0;
        long changed = 0;
        Map<BlockState, Long> counts = container.getBlockCountsMap();
        for (BlockState state : swaps.keySet()) changed += counts.getOrDefault(state, 0L);
        Set<BlockState> after = new HashSet<>(mapping);
        after.removeAll(swaps.keySet());
        boolean clash = false;
        for (BlockState state : swaps.values()) clash |= !after.add(state);
        if (!clash) {
            // still one palette entry per state: the stored ids keep pointing at the same entries, which now hold the new states
            List<BlockState> swapped = new ArrayList<>(mapping.size());
            for (BlockState state : mapping) swapped.add(swaps.getOrDefault(state, state));
            palette.setMapping(swapped);
            return changed;
        }
        // a new state is in the palette already: every block is rewritten (the container counts again by itself)
        Vec3i size = container.getSize();
        for (int y = 0; y < size.getY(); y++) {
            for (int z = 0; z < size.getZ(); z++) {
                for (int x = 0; x < size.getX(); x++) {
                    BlockState swap = swaps.get(container.getBlockState(x, y, z));
                    if (swap != null) container.setBlockState(x, y, z, swap);
                }
            }
        }
        return changed;
    }

    private void refreshLater(MaterialListBase list) {
        if (!this.toRefresh.contains(list)) this.toRefresh.add(list);
        this.waited = 0;
    }

    /** Once the schematic world is rebuilt (or after a while), the replaced lists count again. */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || this.toRefresh.isEmpty() || Minecraft.getMinecraft().world == null) return;
        SchematicPlacementManager manager = DataManager.getSchematicPlacementManager();
        if (manager.hasPendingRebuilds() && ++this.waited < REFRESH_TIMEOUT) return;
        for (MaterialListBase list : this.toRefresh) list.reCreateMaterialList();
        this.toRefresh.clear();
    }
}
