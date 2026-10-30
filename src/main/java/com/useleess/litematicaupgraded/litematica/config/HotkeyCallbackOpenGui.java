package com.useleess.litematicaupgraded.litematica.config;

import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.malilib.gui.config.BaseConfigScreen;
import com.useleess.litematicaupgraded.malilib.input.ActionResult;
import com.useleess.litematicaupgraded.malilib.input.KeyAction;
import com.useleess.litematicaupgraded.malilib.input.KeyBind;
import com.useleess.litematicaupgraded.malilib.input.callback.HotkeyCallback;
import com.useleess.litematicaupgraded.malilib.overlay.message.MessageDispatcher;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.GameWrap;
import com.useleess.litematicaupgraded.litematica.Reference;
import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.gui.AreaSelectionBrowserScreen;
import com.useleess.litematicaupgraded.litematica.gui.ConfigScreen;
import com.useleess.litematicaupgraded.litematica.gui.LoadedSchematicsListScreen;
import com.useleess.litematicaupgraded.litematica.gui.MainMenuScreen;
import com.useleess.litematicaupgraded.litematica.gui.MaterialListScreen;
import com.useleess.litematicaupgraded.litematica.gui.PlacementGridSettingsScreen;
import com.useleess.litematicaupgraded.litematica.gui.RenderLayerEditScreen;
import com.useleess.litematicaupgraded.litematica.gui.SchematicBrowserScreen;
import com.useleess.litematicaupgraded.litematica.gui.SchematicPlacementSettingsScreen;
import com.useleess.litematicaupgraded.litematica.gui.SchematicPlacementSubRegionSettingsScreen;
import com.useleess.litematicaupgraded.litematica.gui.SchematicPlacementsListScreen;
import com.useleess.litematicaupgraded.litematica.gui.SchematicVerifierScreen;
import com.useleess.litematicaupgraded.litematica.materials.MaterialListBase;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SubRegionPlacement;
import com.useleess.litematicaupgraded.litematica.schematic.verifier.SchematicVerifier;
import com.useleess.litematicaupgraded.litematica.schematic.verifier.SchematicVerifierManager;
import com.useleess.litematicaupgraded.litematica.selection.AreaSelectionManager;

public class HotkeyCallbackOpenGui implements HotkeyCallback
{
    @Override
    public ActionResult onKeyAction(KeyAction action, KeyBind key)
    {
        if (GameWrap.getClientPlayer() == null || GameWrap.getClientWorld() == null)
        {
            return ActionResult.FAIL;
        }

        if (key == Hotkeys.OPEN_MAIN_MENU.getKeyBind())
        {
            BaseScreen.openScreen(new MainMenuScreen());
        }
        else if (key == Hotkeys.OPEN_CONFIG_SCREEN.getKeyBind())
        {
            // TODO Shouldn't this be handled by the tabbed screen now?
            BaseScreen screen = BaseConfigScreen.getCurrentTab(Reference.MOD_ID) == ConfigScreen.RENDER_LAYERS ? new RenderLayerEditScreen() : ConfigScreen.create();
            BaseScreen.openScreen(screen);
            return ActionResult.SUCCESS;
        }

        else if (key == Hotkeys.OPEN_AREA_EDITOR_SCREEN.getKeyBind())
        {
            AreaSelectionManager manager = DataManager.getAreaSelectionManager();

            if (manager.getCurrentSelection() != null)
            {
                manager.openEditGui(null);
            }
            else
            {
                MessageDispatcher.error("litematica.message.error.no_area_selected");
            }
        }
        else if (key == Hotkeys.OPEN_LOAD_SCHEMATICS_SCREEN.getKeyBind())
        {
            BaseScreen.openScreen(new SchematicBrowserScreen());
        }
        else if (key == Hotkeys.OPEN_LOADED_SCHEMATICS_SCREEN.getKeyBind())
        {
            BaseScreen.openScreen(new LoadedSchematicsListScreen());
        }
        else if (key == Hotkeys.OPEN_MATERIAL_LIST_SCREEN.getKeyBind())
        {
            MaterialListBase materialList = DataManager.getMaterialList();

            // No last-viewed material list currently stored, try to get one for the currently selected placement, if any
            if (materialList == null)
            {
                SchematicPlacement schematicPlacement = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();

                if (schematicPlacement != null)
                {
                    materialList = schematicPlacement.getMaterialList();
                    materialList.reCreateMaterialList();
                }
                else
                {
                    MessageDispatcher.error("litematica.message.error.no_placement_selected");
                }
            }

            if (materialList != null)
            {
                BaseScreen.openScreen(new MaterialListScreen(materialList));
            }
        }
        else if (key == Hotkeys.OPEN_PLACEMENT_GRID_SETTINGS.getKeyBind())
        {
            SchematicPlacement placement = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();

            if (placement != null)
            {
                if (placement.isRepeatedPlacement() == false)
                {
                    BaseScreen.openScreen(new PlacementGridSettingsScreen(placement));
                }
                else
                {
                    MessageDispatcher.error("litematica.message.error.placement_grid_settings.open_gui_selected_is_grid");
                }
            }
        }
        else if (key == Hotkeys.OPEN_PLACEMENT_SETTINGS_SCREEN.getKeyBind())
        {
            SchematicPlacement schematicPlacement = DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement();

            if (schematicPlacement == null)
            {
                MessageDispatcher.error("litematica.message.error.no_placement_selected");
                return ActionResult.FAIL;
            }

            if (schematicPlacement.isSchematicLoaded() == false)
            {
                MessageDispatcher.error("litematica.message.error.schematic_placement_configure_schematic_not_loaded",
                                        schematicPlacement.getName());
                return ActionResult.FAIL;
            }

            SubRegionPlacement subRegionPlacement = schematicPlacement.getSelectedSubRegionPlacement();

            if (subRegionPlacement != null)
            {
                BaseScreen.openScreen(new SchematicPlacementSubRegionSettingsScreen(schematicPlacement, subRegionPlacement));
            }
            else
            {
                BaseScreen.openScreen(new SchematicPlacementSettingsScreen(schematicPlacement));
            }
        }
        else if (key == Hotkeys.OPEN_PLACEMENTS_LIST_SCREEN.getKeyBind())
        {
            BaseScreen.openScreen(new SchematicPlacementsListScreen());
        }
        else if (key == Hotkeys.OPEN_SCHEMATIC_VCS_SCREEN.getKeyBind())
        {
            MainMenuScreen.openSchematicProjectsScreen();
        }
        else if (key == Hotkeys.OPEN_SCHEMATIC_VERIFIER_SCREEN.getKeyBind())
        {
            SchematicVerifier verifier = SchematicVerifierManager.INSTANCE.getSelectedVerifierOrCreateForPlacement();

            if (verifier != null)
            {
                BaseScreen.openScreen(new SchematicVerifierScreen(verifier));
            }
            else
            {
                MessageDispatcher.error("litematica.message.error.schematic_verifier.no_placement_selected");
            }
        }
        else if (key == Hotkeys.OPEN_AREA_SELECTION_BROWSER.getKeyBind())
        {
            if (DataManager.getSchematicProjectsManager().hasProjectOpen() == false)
            {
                BaseScreen.openScreen(new AreaSelectionBrowserScreen());
            }
            else
            {
                MessageDispatcher.warning("litematica.hover.button.main_menu.area_browser_in_vcs_mode");
            }
        }

        return ActionResult.SUCCESS;
    }
}
