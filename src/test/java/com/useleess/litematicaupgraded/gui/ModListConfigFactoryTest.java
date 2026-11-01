package com.useleess.litematicaupgraded.gui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import net.minecraftforge.fml.common.Mod;

import com.useleess.litematicaupgraded.LitematicaUpgraded;

/** Forge enables the Mods list's "Config" button only for a mod that names a gui factory with a config screen. */
public class ModListConfigFactoryTest {
    @Test
    public void theModNamesTheFactory() {
        assertEquals(ModListConfigFactory.class.getName(), LitematicaUpgraded.class.getAnnotation(Mod.class).guiFactory());
    }

    @Test
    public void theFactoryHasAConfigScreen() {
        ModListConfigFactory factory = new ModListConfigFactory();
        assertTrue(factory.hasConfigGui());
        assertNull(factory.runtimeGuiCategories());
    }
}
