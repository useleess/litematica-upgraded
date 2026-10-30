// Litematica sign text paste (MixinAbstractSignEditScreen), adapted by HackerRouter, 2026 (Schematica Upgraded, MIT / LGPL-3.0).
package com.useleess.litematicaupgraded.printer;

import java.lang.reflect.Field;

import net.minecraft.client.gui.inventory.GuiEditSign;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.useleess.litematicaupgraded.litematica.Litematica;
import com.useleess.litematicaupgraded.litematica.world.SchematicWorldHandler;

/** Fills a newly opened sign editor with the text of the schematic sign at the same position. */
public final class SignTextPaste {
    public static final SignTextPaste INSTANCE = new SignTextPaste();
    private static final Field TILE_SIGN = field();

    private SignTextPaste() {}

    /** GuiEditSign.tileSign by its development or its obfuscated (SRG) name. */
    private static Field field() {
        for (String name : new String[] {"tileSign", "field_146848_f"}) {
            try {
                Field field = GuiEditSign.class.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {}
        }
        Litematica.LOGGER.warn("GuiEditSign.tileSign not found; sign text paste is off");
        return null;
    }

    /** The sign a sign editor edits, or null. */
    public static TileEntitySign signOf(GuiEditSign gui) {
        try {
            return TILE_SIGN == null ? null : (TileEntitySign) TILE_SIGN.get(gui);
        } catch (IllegalAccessException | RuntimeException error) {
            return null;
        }
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (!PrinterConfigs.SIGN_TEXT.getBooleanValue() || !(event.getGui() instanceof GuiEditSign)) return;
        TileEntitySign sign = signOf((GuiEditSign) event.getGui());
        World schematic = SchematicWorldHandler.getSchematicWorld();
        if (sign == null || schematic == null) return;
        BlockPos pos = sign.getPos();
        if (!PrinterArea.insidePlacement(pos.getX(), pos.getY(), pos.getZ())) return;
        TileEntity tile = schematic.getTileEntity(pos);
        if (!(tile instanceof TileEntitySign)) return;
        ITextComponent[] text = ((TileEntitySign) tile).signText;
        for (int i = 0; i < sign.signText.length && i < text.length; i++) {
            String line = text[i] == null ? "" : text[i].getUnformattedText();
            sign.signText[i] = new TextComponentString(line.length() > 15 ? line.substring(0, 15) : line);
        }
    }
}
