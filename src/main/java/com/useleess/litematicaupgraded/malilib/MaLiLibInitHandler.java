package com.useleess.litematicaupgraded.malilib;

import com.useleess.litematicaupgraded.malilib.config.BaseModConfig;
import com.useleess.litematicaupgraded.malilib.input.CustomHotkeyManager;
import com.useleess.litematicaupgraded.malilib.network.message.ConfigLockPacketHandler;
import com.useleess.litematicaupgraded.malilib.overlay.widget.ConfigStatusIndicatorContainerWidget;
import com.useleess.litematicaupgraded.malilib.registry.Registry;

public class MaLiLibInitHandler
{
    public static void registerMalilibHandlers()
    {
        Registry.CONFIG_MANAGER.registerConfigHandler(BaseModConfig.createDefaultModConfig(MaLiLibReference.MOD_INFO, MaLiLibConfigs.CONFIG_VERSION, MaLiLibConfigs.CATEGORIES));
        Registry.CONFIG_SCREEN.registerConfigScreenFactory(MaLiLibReference.MOD_INFO, MaLiLibConfigScreen::create);
        Registry.CONFIG_TAB.registerConfigTabSupplier(MaLiLibReference.MOD_INFO, MaLiLibConfigScreen::getConfigTabs);

        Registry.HOTKEY_MANAGER.registerHotkeyProvider(MaLiLibHotkeyProvider.INSTANCE);
        Registry.HOTKEY_MANAGER.registerHotkeyProvider(CustomHotkeyManager.INSTANCE);
        Registry.HOTKEY_MANAGER.registerHotkeyProvider(ConfigStatusIndicatorContainerWidget.getHotkeyProvider());

        Registry.RENDER_EVENT_DISPATCHER.registerGameOverlayRenderer(Registry.INFO_OVERLAY);
        Registry.RENDER_EVENT_DISPATCHER.registerScreenPostRenderer(Registry.INFO_OVERLAY);
        Registry.TICK_EVENT_DISPATCHER.registerClientTickHandler(Registry.INFO_OVERLAY);

        MaLiLibConfigInit.init();
        MaLiLibActions.init();

        ConfigLockPacketHandler.updateRegistration(true);
    }
}
