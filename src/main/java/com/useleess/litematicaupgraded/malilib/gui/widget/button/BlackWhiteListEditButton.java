package com.useleess.litematicaupgraded.malilib.gui.widget.button;

import java.util.ArrayList;
import java.util.List;

import com.useleess.litematicaupgraded.malilib.config.option.list.BlackWhiteListConfig;
import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.malilib.gui.config.BlackWhiteListEditScreen;
import com.useleess.litematicaupgraded.malilib.util.StringUtils;
import com.useleess.litematicaupgraded.malilib.util.restriction.UsageRestriction.ListType;

public class BlackWhiteListEditButton extends GenericButton
{
    protected final BlackWhiteListConfig<?> config;
    protected final Runnable saveListener;

    public BlackWhiteListEditButton(int width, int height, BlackWhiteListConfig<?> config, Runnable saveListener)
    {
        super(width, height);

        this.config = config;
        this.saveListener = saveListener;

        this.setHoverStringProvider("value_preview", this::generateHoverStrings);
        this.setActionListener(this::openEditScreen);
        this.setDisplayStringSupplier(this::getCurrentDisplayString);
    }

    protected void openEditScreen()
    {
        BaseScreen.openPopupScreenWithCurrentScreenAsParent(new BlackWhiteListEditScreen<>(this.config, this.saveListener));
    }

    protected String getCurrentDisplayString()
    {
        ListType type = this.config.getValue().getListType();

        if (type == ListType.NONE)
        {
            return StringUtils.translate("malilib.button.config.black_white_list.type_none");
        }
        else
        {
            int total = this.config.getValue().getActiveList().getValue().size();
            return StringUtils.translate("malilib.button.config.black_white_list.type_entries", type.getDisplayName(), total);
        }
    }

    protected List<String> generateHoverStrings()
    {
        ListType type = this.config.getValue().getListType();

        if (type != ListType.NONE)
        {
            List<String> hoverStrings = new ArrayList<>();
            List<String> list = this.config.getValue().getActiveListAsString();
            int total = list.size();
            int max = Math.min(10, total);

            hoverStrings.add(StringUtils.translate("malilib.hover.button.config.black_white_list_edit.type", type.getDisplayName()));
            hoverStrings.add(StringUtils.translate("malilib.hover.button.config_list.total_entries", total));

            for (int i = 0; i < max; ++i)
            {
                hoverStrings.add("§7" + list.get(i));
            }

            if (total > max)
            {
                hoverStrings.add(StringUtils.translate("malilib.hover.button.config_list.more_entries", total - max));
            }

            return hoverStrings;
        }

        return EMPTY_STRING_LIST;
    }
}
