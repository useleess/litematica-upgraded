package com.useleess.litematicaupgraded.mixin;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.useleess.litematicaupgraded.litematica.gui.widget.list.entry.MaterialListEntryWidget;
import com.useleess.litematicaupgraded.litematica.materials.MaterialListBase;
import com.useleess.litematicaupgraded.litematica.materials.MaterialListEntry;
import com.useleess.litematicaupgraded.malilib.gui.widget.button.GenericButton;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.BaseDataListEntryWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import com.useleess.litematicaupgraded.materials.MaterialReplace;

/** A "Replace" button on each row of a placement's material list, left of Litematica's own "Ignore" button. */
@Mixin(value = MaterialListEntryWidget.class, remap = false)
public abstract class MixinMaterialListEntryWidgetReplace extends BaseDataListEntryWidget<MaterialListEntry>
{
    @Shadow @Final protected MaterialListBase materialList;
    @Shadow @Final protected GenericButton ignoreButton;

    @Unique
    private GenericButton litematicaUpgradedReplaceButton;

    private MixinMaterialListEntryWidgetReplace(MaterialListEntry data, DataListEntryWidgetData constructData)
    {
        super(data, constructData);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void litematicaUpgradedCreateReplace(MaterialListEntry data, DataListEntryWidgetData constructData, MaterialListBase list, CallbackInfo ci)
    {
        if (MaterialReplace.supports(list))
        {
            this.litematicaUpgradedReplaceButton = GenericButton.create(18, "litematica_upgraded.gui.button.material.replace",
                                                                        () -> MaterialReplace.openPicker(list, data));
            this.litematicaUpgradedReplaceButton.translateAndAddHoverString("litematica_upgraded.gui.hover.material.replace");
        }
    }

    @Inject(method = "reAddSubWidgets", at = @At("TAIL"))
    private void litematicaUpgradedAddReplace(CallbackInfo ci)
    {
        if (this.litematicaUpgradedReplaceButton != null)
        {
            this.addWidget(this.litematicaUpgradedReplaceButton);
        }
    }

    @Inject(method = "updateSubWidgetPositions", at = @At("TAIL"))
    private void litematicaUpgradedPlaceReplace(CallbackInfo ci)
    {
        if (this.litematicaUpgradedReplaceButton != null)
        {
            this.litematicaUpgradedReplaceButton.setRight(this.ignoreButton.getX() - 2);
            this.litematicaUpgradedReplaceButton.centerVerticallyInside(this);
        }
    }
}
