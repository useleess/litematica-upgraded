package com.useleess.litematicaupgraded.mixin;

import java.nio.file.Path;
import javax.annotation.Nullable;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.useleess.litematicaupgraded.litematica.gui.util.AbstractSchematicInfoCache.SchematicInfo;
import com.useleess.litematicaupgraded.litematica.gui.widget.AbstractSchematicInfoWidget;
import com.useleess.litematicaupgraded.malilib.gui.icon.BaseIcon;
import com.useleess.litematicaupgraded.malilib.gui.util.ScreenContext;
import com.useleess.litematicaupgraded.malilib.gui.widget.ContainerWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.IconWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.LabelWidget;
import com.useleess.litematicaupgraded.preview.PreviewConfigs;
import com.useleess.litematicaupgraded.preview.PreviewLayout;
import com.useleess.litematicaupgraded.preview.PreviewView;

/**
 * The 3D view (turns on its own; drag to turn, wheel to zoom) in the empty bottom part of the schematic browser's info panel. The saved picture (when the file has one)
 * moves up under the detail lines and shrinks to half the room, so the 3D view gets the rest and nothing overlaps.
 */
@Mixin(value = AbstractSchematicInfoWidget.class, remap = false)
public abstract class MixinSchematicInfoWidgetPreview extends ContainerWidget
{
    @Shadow @Final protected LabelWidget infoTextLabel;
    @Shadow @Final protected LabelWidget descriptionLabel;
    @Shadow @Final protected IconWidget iconWidget;
    @Shadow @Nullable protected SchematicInfo currentInfo;
    @Shadow protected boolean hasDescription;

    @Unique
    @Nullable
    private Path litematicaUpgradedFile;

    private MixinSchematicInfoWidgetPreview(int width, int height)
    {
        super(width, height);
    }

    @Inject(method = "setActiveEntry", at = @At("HEAD"))
    private void litematicaUpgradedSelect(Object entry, CallbackInfo ci)
    {
        this.litematicaUpgradedFile = entry instanceof Path ? (Path) entry : null;
        this.canReceiveMouseClicks = true;
        this.canReceiveMouseScrolls = true;
        PreviewView.INSTANCE.show(this.litematicaUpgradedFile);
    }

    @Inject(method = "updatePreviewIcon", at = @At("TAIL"))
    private void litematicaUpgradedShrinkIcon(CallbackInfo ci)
    {
        if (this.litematicaUpgradedFile == null || this.currentInfo == null || this.iconWidget.getIcon() == null ||
            PreviewConfigs.ENABLED.getBooleanValue() == false)
        {
            return;
        }

        int room = this.getHeight() - this.infoTextLabel.getHeight() - 12 - (this.hasDescription ? this.descriptionLabel.getHeight() + 6 : 0);
        int size = Math.min(this.iconWidget.getIcon().getWidth(), room / 2);
        this.iconWidget.setIcon(size < 10 ? null : new BaseIcon(0, 0, size, size, size, size, this.currentInfo.iconName));
    }

    @Inject(method = "updateSubWidgetPositions", at = @At("TAIL"))
    private void litematicaUpgradedMoveIcon(CallbackInfo ci)
    {
        if (this.litematicaUpgradedFile != null && this.currentInfo != null && this.iconWidget.getIcon() != null &&
            PreviewConfigs.ENABLED.getBooleanValue())
        {
            this.iconWidget.setY(this.litematicaUpgradedContentBottom(this.getY(), false) + 4);
        }
    }

    /** The lowest y of the detail lines (and of the picture when {@code withIcon}). */
    @Unique
    private int litematicaUpgradedContentBottom(int top, boolean withIcon)
    {
        int bottom = Math.max(top, this.infoTextLabel.getBottom());

        if (this.hasDescription)
        {
            bottom = Math.max(bottom, this.descriptionLabel.getBottom());
        }

        if (withIcon && this.iconWidget.getIcon() != null)
        {
            bottom = Math.max(bottom, this.iconWidget.getBottom());
        }

        return bottom;
    }

    @Override
    public void renderAt(int x, int y, float z, ScreenContext ctx)
    {
        super.renderAt(x, y, z, ctx);

        if (this.litematicaUpgradedFile == null)
        {
            return;
        }

        int dx = x - this.getX();
        int dy = y - this.getY();
        int[] view = PreviewLayout.view(x, y, this.getWidth(), this.getHeight(),
                                         this.litematicaUpgradedContentBottom(this.getY(), true) + dy);

        if (view != null)
        {
            PreviewView.INSTANCE.draw(this.litematicaUpgradedFile, view[0], view[1], view[2], view[3], z, ctx.mouseX, ctx.mouseY);
        }
    }

    @Override
    protected boolean onMouseClicked(int mouseX, int mouseY, int mouseButton)
    {
        return (mouseButton == 0 && PreviewView.INSTANCE.press(mouseX, mouseY)) || super.onMouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void onMouseReleased(int mouseX, int mouseY, int mouseButton)
    {
        PreviewView.INSTANCE.releaseMouse();
        super.onMouseReleased(mouseX, mouseY, mouseButton);
    }

    @Override
    protected boolean onMouseScrolled(int mouseX, int mouseY, double verticalWheelDelta, double horizontalWheelDelta)
    {
        return PreviewView.INSTANCE.scroll(mouseX, mouseY, verticalWheelDelta) ||
               super.onMouseScrolled(mouseX, mouseY, verticalWheelDelta, horizontalWheelDelta);
    }
}
