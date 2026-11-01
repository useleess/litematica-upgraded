package com.useleess.litematicaupgraded.printer;

import java.util.List;

import com.google.common.collect.ImmutableList;
import net.minecraftforge.common.MinecraftForge;

import com.useleess.litematicaupgraded.litematica.Reference;
import com.useleess.litematicaupgraded.litematica.gui.ConfigScreen;
import com.useleess.litematicaupgraded.malilib.config.JsonModConfig;
import com.useleess.litematicaupgraded.malilib.event.InitializationHandler;
import com.useleess.litematicaupgraded.malilib.gui.config.BaseConfigTab;
import com.useleess.litematicaupgraded.malilib.input.Hotkey;
import com.useleess.litematicaupgraded.malilib.input.HotkeyCategory;
import com.useleess.litematicaupgraded.malilib.input.HotkeyProvider;
import com.useleess.litematicaupgraded.malilib.registry.Registry;

/** Hooks the printer into Litematica: the "Litematica Upgraded" config tab, its saved settings, the toggle hotkey, the tick handlers. */
public class PrinterInit implements InitializationHandler {
    /** Made when malilib's initialisation reaches us, not at class loading: building configs earlier upsets malilib's own static setup. */
    public static BaseConfigTab TAB;

    /** Called once from the mod's init event. */
    public static void init() {
        MinecraftForge.EVENT_BUS.register(Printer.INSTANCE);
        MinecraftForge.EVENT_BUS.register(ManualMining.INSTANCE);
        MinecraftForge.EVENT_BUS.register(SignTextPaste.INSTANCE);
        MinecraftForge.EVENT_BUS.register(DestroyAssist.INSTANCE);
        MinecraftForge.EVENT_BUS.register(com.useleess.litematicaupgraded.materials.MaterialReplace.INSTANCE);
        MinecraftForge.EVENT_BUS.register(com.useleess.litematicaupgraded.preview.PreviewView.INSTANCE);
        Registry.INITIALIZATION_DISPATCHER.registerInitializationHandler(new PrinterInit());
    }

    @Override
    public void registerModHandlers() {
        com.useleess.litematicaupgraded.perf.PerfConfigs.wire();
        TAB = new BaseConfigTab(PrinterConfigs.MOD_INFO, "printer", PrinterConfigs.MOD_INFO.getModId() + ".config.tab.printer", 220,
                                PrinterConfigs.OPTIONS, ConfigScreen::create);
        Registry.CONFIG_MANAGER.registerConfigHandler(JsonModConfig.createJsonModConfig(PrinterConfigs.MOD_INFO, PrinterConfigs.CURRENT_VERSION, PrinterConfigs.CATEGORIES, null));
        // the tab is added to Litematica's own config screen, after its own tabs
        Registry.CONFIG_TAB.registerExtensionModConfigTabSupplier(Reference.MOD_INFO, () -> ImmutableList.of(TAB));
        Registry.CONFIG_TAB.registerExtensionModConfigScreenTabSupplier(Reference.MOD_INFO, () -> ImmutableList.of(TAB));
        Registry.HOTKEY_MANAGER.registerHotkeyProvider(new Provider());
    }

    public static class Provider implements HotkeyProvider {
        @Override
        public List<? extends Hotkey> getAllHotkeys() {
            return PrinterConfigs.HOTKEYS;
        }

        @Override
        public List<HotkeyCategory> getHotkeysByCategories() {
            return ImmutableList.of(new HotkeyCategory(PrinterConfigs.MOD_INFO, PrinterConfigs.MOD_INFO.getModId() + ".hotkeys.category.printer", PrinterConfigs.HOTKEYS));
        }
    }
}
