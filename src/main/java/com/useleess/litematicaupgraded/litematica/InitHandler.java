package com.useleess.litematicaupgraded.litematica;

import com.useleess.litematicaupgraded.malilib.config.JsonModConfig;
import com.useleess.litematicaupgraded.malilib.config.JsonModConfig.ConfigDataUpdater;
import com.useleess.litematicaupgraded.malilib.config.util.ConfigUpdateUtils.KeyBindSettingsResetter;
import com.useleess.litematicaupgraded.malilib.event.InitializationHandler;
import com.useleess.litematicaupgraded.malilib.registry.Registry;
import com.useleess.litematicaupgraded.litematica.config.Configs;
import com.useleess.litematicaupgraded.litematica.config.HotkeyCallbacks;
import com.useleess.litematicaupgraded.litematica.data.FileMigrationUtils;
import com.useleess.litematicaupgraded.litematica.event.ClientWorldChangeHandler;
import com.useleess.litematicaupgraded.litematica.event.RenderHandler;
import com.useleess.litematicaupgraded.litematica.gui.ConfigScreen;
import com.useleess.litematicaupgraded.litematica.input.LitematicaHotkeyProvider;
import com.useleess.litematicaupgraded.litematica.input.MouseScrollHandlerImpl;
import com.useleess.litematicaupgraded.litematica.network.SchematicSavePacketHandler;
import com.useleess.litematicaupgraded.litematica.render.infohud.StatusInfoRenderer;
import com.useleess.litematicaupgraded.litematica.scheduler.ClientTickHandler;
import com.useleess.litematicaupgraded.litematica.util.LitematicaDirectories;

public class InitHandler implements InitializationHandler
{
    @Override
    public void registerModHandlers()
    {
        // Reset all KeyBindSettings when updating to the first post-malilib-refactor version
        ConfigDataUpdater updater = new KeyBindSettingsResetter(LitematicaHotkeyProvider.INSTANCE::getAllHotkeys, 0);
        Registry.CONFIG_MANAGER.registerConfigHandler(JsonModConfig.createJsonModConfig(Reference.MOD_INFO, Configs.CURRENT_VERSION, Configs.CATEGORIES, updater));

        Registry.CONFIG_SCREEN.registerConfigScreenFactory(Reference.MOD_INFO, ConfigScreen::create);
        Registry.CONFIG_TAB.registerConfigTabSupplier(Reference.MOD_INFO, ConfigScreen::getConfigTabs);

        Registry.HOTKEY_MANAGER.registerHotkeyProvider(new LitematicaHotkeyProvider());
        Registry.INPUT_DISPATCHER.registerMouseScrollHandler(new MouseScrollHandlerImpl());

        RenderHandler renderer = new RenderHandler();
        Registry.RENDER_EVENT_DISPATCHER.registerGameOverlayRenderer(renderer);
        Registry.RENDER_EVENT_DISPATCHER.registerWorldPostRenderer(renderer);

        Registry.TICK_EVENT_DISPATCHER.registerClientTickHandler(new ClientTickHandler());

        Registry.CLIENT_WORLD_CHANGE_EVENT_DISPATCHER.registerClientWorldChangeHandler(new ClientWorldChangeHandler());

        Configs.init();
        FileMigrationUtils.tryMigrateOldPerWorldData();
        FileMigrationUtils.tryMigrateOldAreaSelections();

        HotkeyCallbacks.init();
        StatusInfoRenderer.init();

        // This creates the directories if they don't exist yet
        LitematicaDirectories.getAreaSelectionsBaseDirectory();
        LitematicaDirectories.getSchematicsBaseDirectory();

        Registry.CLIENT_PACKET_CHANNEL_HANDLER.registerClientChannelHandler(SchematicSavePacketHandler.INSTANCE);
    }
}
