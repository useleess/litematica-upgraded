package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry;

import com.useleess.litematicaupgraded.malilib.gui.widget.BlockModelWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.InteractableWidget;
import com.useleess.litematicaupgraded.malilib.render.text.StyledTextLine;
import com.useleess.litematicaupgraded.malilib.util.world.BlockState;

public class BlockStateEntryWidget extends BaseDataListEntryWidget<BlockState>
{
    protected final InteractableWidget blockModelWidget;
    protected boolean useModel = true;

    public BlockStateEntryWidget(BlockState data, DataListEntryWidgetData constructData)
    {
        super(data, constructData);

        this.blockModelWidget = new BlockModelWidget(data.vanillaState());
        this.blockModelWidget.getBackgroundRenderer().getNormalSettings().setEnabledAndColor(true, 0xFF202020);

        this.setText(StyledTextLine.parseFirstLine(data.getFullStateString()));
    }

    @Override
    public void reAddSubWidgets()
    {
        super.reAddSubWidgets();

        this.addWidgetIf(this.blockModelWidget, this.useModel);
    }

    @Override
    public void updateSubWidgetPositions()
    {
        super.updateSubWidgetPositions();

        if (this.useModel)
        {
            this.getTextOffset().setXOffset(22);
            this.blockModelWidget.setX(this.getX() + 2);
            this.blockModelWidget.centerVerticallyInside(this);
        }
        else
        {
            this.getTextOffset().setXOffset(2);
        }
    }

    public void setUseModel(boolean useModel)
    {
        this.useModel = useModel;
    }
}
