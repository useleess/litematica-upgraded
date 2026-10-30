package com.useleess.litematicaupgraded.litematica.gui.widget.list.entry;

import java.util.ArrayList;
import java.util.List;

import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.malilib.gui.icon.DefaultIcons;
import com.useleess.litematicaupgraded.malilib.gui.widget.IconWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.button.GenericButton;
import com.useleess.litematicaupgraded.malilib.gui.widget.button.OnOffButton;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.BaseDataListEntryWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import com.useleess.litematicaupgraded.malilib.render.text.StyledTextLine;
import com.useleess.litematicaupgraded.malilib.util.StringUtils;
import com.useleess.litematicaupgraded.malilib.util.position.BlockPos;
import com.useleess.litematicaupgraded.malilib.util.position.Vec3i;
import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.litematica.gui.SchematicPlacementSubRegionSettingsScreen;
import com.useleess.litematicaupgraded.litematica.schematic.SchematicRegion;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacement;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SchematicPlacementManager;
import com.useleess.litematicaupgraded.litematica.schematic.placement.SubRegionPlacement;
import com.useleess.litematicaupgraded.litematica.util.PositionUtils;

public class SchematicPlacementSubRegionEntryWidget extends BaseDataListEntryWidget<SubRegionPlacement>
{
    protected final SchematicPlacementManager manager;
    protected final SchematicPlacement placement;
    protected final GenericButton configureButton;
    protected final GenericButton toggleEnabledButton;
    protected final IconWidget modificationNoticeIcon;
    protected int buttonsStartX;

    public SchematicPlacementSubRegionEntryWidget(SubRegionPlacement data,
                                                  DataListEntryWidgetData constructData,
                                                  SchematicPlacement placement)
    {
        super(data, constructData);

        this.manager = DataManager.getSchematicPlacementManager();
        this.placement = placement;

        this.configureButton     = GenericButton.create(18, "litematica.button.schematic_placements_list.configure", this::openConfigurationMenu);
        this.toggleEnabledButton = OnOffButton.simpleSlider(18, data::isEnabled, this::toggleSubRegionEnabled);
        this.modificationNoticeIcon = new IconWidget(DefaultIcons.EXCLAMATION_11);
        this.modificationNoticeIcon.translateAndAddHoverString("litematica.hover.placement_list.icon.placement_modified");

        String key = data.isEnabled() ? "litematica.button.schematic_placement_settings.entry_name.enabled" :
                                        "litematica.button.schematic_placement_settings.entry_name.disabled";
        this.setText(StyledTextLine.translateFirstLine(key, data.getDisplayName()));

        this.getBackgroundRenderer().getNormalSettings().setEnabledAndColor(true, this.isOdd ? 0xA0101010 : 0xA0303030);
        this.getBackgroundRenderer().getHoverSettings().setEnabledAndColor(true, 0xA0707070);
        this.addHoverInfo();
    }

    @Override
    public void reAddSubWidgets()
    {
        super.reAddSubWidgets();

        this.addWidget(this.configureButton);
        this.addWidget(this.toggleEnabledButton);

        if (this.data.isRegionPlacementModifiedFromDefault())
        {
            this.addWidget(this.modificationNoticeIcon);
        }
    }

    @Override
    public void updateSubWidgetPositions()
    {
        super.updateSubWidgetPositions();

        this.modificationNoticeIcon.centerVerticallyInside(this);
        this.configureButton.centerVerticallyInside(this);
        this.toggleEnabledButton.centerVerticallyInside(this);

        this.toggleEnabledButton.setRight(this.getRight() - 2);
        this.configureButton.setRight(this.toggleEnabledButton.getX() - 1);
        this.modificationNoticeIcon.setRight(this.configureButton.getX() - 2);

        this.buttonsStartX = this.modificationNoticeIcon.getX() - 1;
    }

    @Override
    protected boolean isSelected()
    {
        SchematicPlacement placement = this.manager.getSelectedSchematicPlacement();
        return placement != null && placement.getSelectedSubRegionPlacement() == this.data;
    }

    @Override
    public boolean canHoverAt(int mouseX, int mouseY)
    {
        return mouseX <= this.buttonsStartX && super.canHoverAt(mouseX, mouseY);
    }

    protected void openConfigurationMenu()
    {
        BaseScreen.openScreenWithParent(new SchematicPlacementSubRegionSettingsScreen(this.placement, this.data));
    }

    protected void toggleSubRegionEnabled()
    {
        DataManager.getSchematicPlacementManager().toggleSubRegionEnabled(this.placement, this.data.getName());
        this.listWidget.refreshEntries();
    }

    protected void addHoverInfo()
    {
        List<String> lines = new ArrayList<>();

        if (this.data.isRegionPlacementModifiedFromDefault())
        {
            lines.add(StringUtils.translate("litematica.hover.placement_list.sub_region_modified"));
        }

        SchematicRegion region = this.placement.getSchematic().getRegions().get(this.data.getName());
        Vec3i size = region != null ? region.getSize() : null;

        if (size != null)
        {
            lines.add(StringUtils.translate("litematica.hover.placement_list.sub_region_size",
                                            size.getX(), size.getY(), size.getZ()));
        }

        lines.add(StringUtils.translate("litematica.hover.placement_list.rotation",
                                        this.data.getRotation().getDisplayName()));
        lines.add(StringUtils.translate("litematica.hover.placement_list.mirror",
                                        this.data.getMirror().getDisplayName()));

        BlockPos relativeOrigin = this.data.getPosition();
        BlockPos o = PositionUtils.getTransformedBlockPos(relativeOrigin, this.placement.getMirror(),
                                                          this.placement.getRotation()).add(this.placement.getPosition());
        lines.add(StringUtils.translate("litematica.hover.placement_list.origin", o.getX(), o.getY(), o.getZ()));

        this.getHoverInfoFactory().addStrings(lines);
    }
}
