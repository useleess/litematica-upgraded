package com.useleess.litematicaupgraded.compat;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiErrorScreen;
import net.minecraft.client.resources.I18n;
import net.minecraftforge.fml.client.CustomModLoadingErrorDisplayException;

/** The start-up error screen for a missing Mixin (client only; the text comes from the language file). */
final class ClientError extends CustomModLoadingErrorDisplayException {
    private ClientError() {
        super(MixinCheck.ENGLISH, null);
    }

    /** Typed as RuntimeException so a dedicated server (no client classes) can verify the caller without loading this class. */
    static RuntimeException create() {
        return new ClientError();
    }

    @Override
    public void initGui(GuiErrorScreen errorScreen, FontRenderer fontRenderer) {
    }

    @Override
    public void drawScreen(GuiErrorScreen errorScreen, FontRenderer fontRenderer, int mouseRelX, int mouseRelY, float tickTime) {
        errorScreen.drawCenteredString(fontRenderer, I18n.format(MixinCheck.MESSAGE_KEY), errorScreen.width / 2, errorScreen.height / 2, 0xFFFFFF);
    }
}
