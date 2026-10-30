package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.action;

import com.useleess.litematicaupgraded.malilib.MaLiLibConfigs;
import com.useleess.litematicaupgraded.malilib.action.NamedAction;
import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.BaseDataListEntryWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import com.useleess.litematicaupgraded.malilib.render.text.StyledTextLine;
import com.useleess.litematicaupgraded.malilib.render.text.StyledTextUtils;
import com.useleess.litematicaupgraded.malilib.util.data.LeftRight;

public class ActionPromptEntryWidget extends BaseDataListEntryWidget<NamedAction>
{
    public ActionPromptEntryWidget(NamedAction data,
                                   DataListEntryWidgetData constructData)
    {
        super(data, constructData);

        this.canReceiveMouseClicks = true;
        this.getBorderRenderer().getHoverSettings().setBorderWidthAndColor(1, 0xFFF0B000);
        this.getBackgroundRenderer().getHoverSettings().setEnabled(false);

        StyledTextLine fullName = data.getColoredWidgetDisplayName();
        this.setText(StyledTextUtils.clampStyledTextToMaxWidth(fullName, this.getWidth() - 16, LeftRight.RIGHT, " ..."));

        this.getHoverInfoFactory().setTextLineProvider("action_info", data::getHoverInfo);
    }

    @Override
    protected boolean onMouseClicked(int mouseX, int mouseY, int mouseButton)
    {
        if (MaLiLibConfigs.Generic.ACTION_PROMPT_CLOSE_ON_EXECUTE.getBooleanValue())
        {
            // Close the current screen first, in case the action opens another screen
            BaseScreen.openScreen(null);
        }

        this.data.execute();
        return true;
    }
}
