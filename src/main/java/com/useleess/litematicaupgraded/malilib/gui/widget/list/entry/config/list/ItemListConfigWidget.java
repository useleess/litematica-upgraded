package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.list;

import net.minecraft.init.Items;
import net.minecraft.item.Item;

import com.useleess.litematicaupgraded.malilib.config.option.list.ItemListConfig;
import com.useleess.litematicaupgraded.malilib.gui.config.ConfigWidgetContext;
import com.useleess.litematicaupgraded.malilib.gui.widget.ItemStackWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.button.BaseValueListEditButton;
import com.useleess.litematicaupgraded.malilib.gui.widget.button.GenericButton;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import com.useleess.litematicaupgraded.malilib.util.StringUtils;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.RegistryUtils;

public class ItemListConfigWidget extends BaseValueListConfigWidget<Item, ItemListConfig>
{
    public ItemListConfigWidget(ItemListConfig config,
                                DataListEntryWidgetData constructData,
                                ConfigWidgetContext ctx)
    {
        super(config, constructData, ctx);
    }

    @Override
    protected GenericButton createButton(int width, int height, ItemListConfig config, ConfigWidgetContext ctx)
    {
        String title = StringUtils.translate("malilib.title.screen.item_list_edit", this.config.getDisplayName());

        return new BaseValueListEditButton<>(width, height,
                                             config,
                                             this::updateWidgetState,
                                             () -> Items.STICK,
                                             RegistryUtils::getSortedItemList,
                                             RegistryUtils::getItemIdStr,
                                             ItemStackWidget::createItemWidget,
                                             title);
    }
}
