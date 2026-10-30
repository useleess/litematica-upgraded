package com.useleess.litematicaupgraded.litematica.input;

import net.minecraft.entity.Entity;

import com.useleess.litematicaupgraded.malilib.gui.util.GuiUtils;
import com.useleess.litematicaupgraded.malilib.input.MouseScrollHandler;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageDispatcher;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.EntityWrap;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.GameWrap;
import com.useleess.litematicaupgraded.malilib.util.position.BlockPos;
import com.useleess.litematicaupgraded.malilib.util.position.Direction;
import com.useleess.litematicaupgraded.litematica.config.Configs;
import com.useleess.litematicaupgraded.litematica.config.Hotkeys;
import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.selection.AreaSelection;
import com.useleess.litematicaupgraded.litematica.selection.AreaSelectionManager;
import com.useleess.litematicaupgraded.litematica.selection.BoxCorner;
import com.useleess.litematicaupgraded.litematica.selection.CornerDefinedBox;
import com.useleess.litematicaupgraded.litematica.tool.ToolMode;
import com.useleess.litematicaupgraded.litematica.util.EntityUtils;
import com.useleess.litematicaupgraded.litematica.util.PositionUtils;
import com.useleess.litematicaupgraded.litematica.util.ToolUtils;

public class MouseScrollHandlerImpl implements MouseScrollHandler
{
    @Override
    public boolean onMouseScroll(int mouseX, int mouseY, double deltaX, double deltaY)
    {
        boolean toolEnabled = Configs.Visuals.MAIN_RENDERING_TOGGLE.getBooleanValue() &&
                              Configs.Generic.TOOL_ITEM_ENABLED.getBooleanValue();

        if (GuiUtils.isScreenOpen() ||
            GameWrap.getClientWorld() == null ||
            GameWrap.getClientPlayer() == null ||
            deltaY == 0 ||
            toolEnabled == false ||
            EntityUtils.hasToolItem() == false)
        {
            return false;
        }

        final int amount = deltaY > 0 ? 1 : -1;
        ToolMode mode = DataManager.getToolMode();
        Entity cameraEntity = GameWrap.getCameraEntity();
        Direction direction = EntityWrap.getClosestLookingDirection(cameraEntity);

        if (Hotkeys.SELECTION_EXPAND_MODIFIER.getKeyBind().isKeyBindHeld() && mode.getUsesAreaSelection())
        {
            return this.modifySelectionBox(amount, mode, direction, PositionUtils::expandOrShrinkBox);
        }

        if (Hotkeys.SELECTION_GRAB_MODIFIER.getKeyBind().isKeyBindHeld() && mode.getUsesAreaSelection())
        {
            AreaSelectionManager sm = DataManager.getAreaSelectionManager();

            if (sm.hasSelectedOrigin())
            {
                AreaSelection area = sm.getCurrentSelection();
                BlockPos old = area.getEffectiveOrigin();
                area.moveEntireAreaSelectionTo(old.offset(EntityWrap.getClosestLookingDirection(cameraEntity), amount), false);
                return true;
            }
            else if (mode == ToolMode.MOVE)
            {
                ToolUtils.moveCurrentlySelectedWorldRegionToLookingDirection(amount, cameraEntity);
                return true;
            }
        }

        if (Hotkeys.SELECTION_GROW_MODIFIER.getKeyBind().isKeyBindHeld())
        {
            return this.modifySelectionBox(amount, mode, direction, (boxIn, amountIn, side) -> PositionUtils.growOrShrinkBox(boxIn, amountIn));
        }

        if (Hotkeys.SELECTION_NUDGE_MODIFIER.getKeyBind().isKeyBindHeld())
        {
            return nudgeSelection(amount, mode, cameraEntity);
        }

        if (Hotkeys.OPERATION_MODE_CHANGE_MODIFIER.getKeyBind().isKeyBindHeld())
        {
            DataManager.setToolMode(DataManager.getToolMode().cycle(amount < 0));
            return true;
        }

        if (Hotkeys.SCHEMATIC_VERSION_CYCLE_MODIFIER.getKeyBind().isKeyBindHeld())
        {
            if (DataManager.getSchematicProjectsManager().hasProjectOpen())
            {
                DataManager.getSchematicProjectsManager().cycleVersion(amount * -1);
            }
            return true;
        }

        return false;
    }

    public static boolean nudgeSelection(int amount, ToolMode mode, Entity entity)
    {
        if (mode.getUsesAreaSelection())
        {
            DataManager.getAreaSelectionManager().moveSelectedElement(EntityWrap.getClosestLookingDirection(entity), amount);
            return true;
        }
        else if (mode.getUsesSchematic())
        {
            Direction direction = EntityWrap.getClosestLookingDirection(entity);
            DataManager.getSchematicPlacementManager().nudgePositionOfCurrentSelection(direction, amount);
            return true;
        }

        return false;
    }

    private boolean modifySelectionBox(int amount, ToolMode mode, Direction direction, IBoxEditor editor)
    {
        AreaSelectionManager sm = DataManager.getAreaSelectionManager();
        AreaSelection area = sm.getCurrentSelection();

        if (area != null)
        {
            CornerDefinedBox box = area.getSelectedSelectionBox();

            if (box != null)
            {
                CornerDefinedBox newBox = editor.editBox(box, amount, direction);
                area.setSelectedSelectionBoxCornerPos(newBox.getCorner1(), BoxCorner.CORNER_1);
                area.setSelectedSelectionBoxCornerPos(newBox.getCorner2(), BoxCorner.CORNER_2);
            }
            else
            {
                MessageDispatcher.error().translate("litematica.error.area_selection.no_sub_region_selected");
            }
        }
        else
        {
            MessageDispatcher.error().translate("litematica.message.error.no_area_selected");
        }

        return true;
    }

    private interface IBoxEditor
    {
        CornerDefinedBox editBox(CornerDefinedBox boxIn, int amount, Direction side);
    }
}
