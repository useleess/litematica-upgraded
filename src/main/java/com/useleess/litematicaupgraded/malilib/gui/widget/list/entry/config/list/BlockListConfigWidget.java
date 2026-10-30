package com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.config.list;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import com.useleess.litematicaupgraded.malilib.config.option.list.BlockListConfig;
import com.useleess.litematicaupgraded.malilib.gui.config.ConfigWidgetContext;
import com.useleess.litematicaupgraded.malilib.gui.widget.BlockModelWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.button.BaseValueListEditButton;
import com.useleess.litematicaupgraded.malilib.gui.widget.button.GenericButton;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import com.useleess.litematicaupgraded.malilib.util.StringUtils;
import com.useleess.litematicaupgraded.malilib.util.game.wrap.RegistryUtils;

public class BlockListConfigWidget extends BaseValueListConfigWidget<Block, BlockListConfig>
{
    public BlockListConfigWidget(BlockListConfig config,
                                 DataListEntryWidgetData constructData,
                                 ConfigWidgetContext ctx)
    {
        super(config, constructData, ctx);
    }

    @Override
    protected GenericButton createButton(int width, int height, BlockListConfig config, ConfigWidgetContext ctx)
    {
        String title = StringUtils.translate("malilib.title.screen.block_list_edit", this.config.getDisplayName());

        return new BaseValueListEditButton<>(width, height,
                                             config,
                                             this::updateWidgetState,
                                             () -> Blocks.STONE,
                                             RegistryUtils::getSortedBlockList,
                                             RegistryUtils::getBlockIdStr,
                                             BlockModelWidget::new,
                                             title);
    }
}
