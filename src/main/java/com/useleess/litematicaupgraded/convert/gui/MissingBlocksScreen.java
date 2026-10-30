package com.useleess.litematicaupgraded.convert.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;

import com.useleess.litematicaupgraded.convert.BlockStates;
import com.useleess.litematicaupgraded.convert.MissingBlockGuesses;
import com.useleess.litematicaupgraded.convert.MissingBlocks;
import com.useleess.litematicaupgraded.malilib.gui.BaseListScreen;
import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.malilib.gui.StringListSelectionScreen;
import com.useleess.litematicaupgraded.malilib.gui.widget.button.GenericButton;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.DataListWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.BaseDataListEntryWidget;
import com.useleess.litematicaupgraded.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import com.useleess.litematicaupgraded.malilib.render.text.StyledTextLine;
import com.useleess.litematicaupgraded.malilib.util.StringUtils;

/**
 * The blocks of a schematic file that Minecraft 1.12 does not have, one row per block, with the block used in its place
 * (a saved pick, else the closest block). "Use these" saves the picks and converts the file on disk.
 */
public class MissingBlocksScreen extends BaseListScreen<DataListWidget<MissingBlocksScreen.Row>>
{
    protected final Path file;
    protected final List<Row> rows = new ArrayList<>();
    protected final Consumer<MissingBlocksScreen> onUse;
    protected final Runnable onSkip;
    protected final GenericButton useTheseButton;
    protected final GenericButton leaveOutButton;
    protected final GenericButton guessesButton;
    protected final GenericButton skipButton;
    protected boolean finished;

    public static final class Row
    {
        public final String name;
        public final long count;
        /** The block the file was read with, and the one picked here. */
        public final IBlockState loaded;
        public IBlockState picked;

        Row(String name, long count)
        {
            this.name = name;
            this.count = count;
            this.loaded = MissingBlocks.replacement(name);
            this.picked = this.loaded;
        }
    }

    /**
     * @param onUse called after the picks are saved ("Use these"); converts the file
     * @param onSkip called when the file is loaded as it is instead
     */
    public MissingBlocksScreen(Path file, Map<String, Long> missing, Consumer<MissingBlocksScreen> onUse, Runnable onSkip)
    {
        super(12, 30, 20, 58);

        this.file = file;
        this.onUse = onUse;
        this.onSkip = onSkip;

        for (Map.Entry<String, Long> entry : missing.entrySet())
        {
            this.rows.add(new Row(entry.getKey(), entry.getValue()));
        }

        this.rows.sort((a, b) -> Long.compare(b.count, a.count));

        this.useTheseButton = GenericButton.create("litematica_upgraded.gui.button.missing.use_these", this::useThese);
        this.useTheseButton.translateAndAddHoverString("litematica_upgraded.gui.hover.missing.use_these");
        this.leaveOutButton = GenericButton.create("litematica_upgraded.gui.button.missing.leave_out", () -> this.pickAll(r -> Blocks.AIR.getDefaultState()));
        this.guessesButton = GenericButton.create("litematica_upgraded.gui.button.missing.guesses", () -> this.pickAll(r -> MissingBlocks.guessed(r.name)));
        this.guessesButton.translateAndAddHoverString("litematica_upgraded.gui.hover.missing.guesses");
        this.skipButton = GenericButton.create("litematica_upgraded.gui.button.missing.skip", this::skip);
        this.skipButton.translateAndAddHoverString("litematica_upgraded.gui.hover.missing.skip");

        this.setTitle("litematica_upgraded.title.screen.missing_blocks", file.getFileName().toString());
    }

    public List<Row> getRows()
    {
        return this.rows;
    }

    protected void pickAll(Function<Row, IBlockState> picker)
    {
        for (Row row : this.rows)
        {
            row.picked = picker.apply(row);
        }

        this.getListWidget().refreshEntries();
    }

    /** Picks a block for one row. */
    public void pick(Row row, IBlockState state)
    {
        row.picked = state;
        this.getListWidget().refreshEntries();
    }

    /** The picks of the rows as 1.12 state strings by missing block name. */
    public Map<String, String> getPicks()
    {
        Map<String, String> picks = new LinkedHashMap<>();

        for (Row row : this.rows)
        {
            picks.put(row.name, BlockStates.toString(row.picked));
        }

        return picks;
    }

    protected void useThese()
    {
        if (this.finished)
        {
            return;
        }

        this.finished = true;
        MissingBlocks.choose(this.getPicks());
        this.openParentScreen();
        this.onUse.accept(this);
    }

    protected void skip()
    {
        if (this.finished)
        {
            return;
        }

        this.finished = true;
        this.openParentScreen();
        this.onSkip.run();
    }

    protected void openPicker(Row row)
    {
        List<String> names = new ArrayList<>();
        names.add("minecraft:air");

        for (Object key : Block.REGISTRY.getKeys())
        {
            String name = key.toString();

            if (name.equals("minecraft:air") == false)
            {
                names.add(name);
            }
        }

        StringListSelectionScreen screen = new StringListSelectionScreen(names, selected -> {
            if (selected.isEmpty() == false)
            {
                Block block = BlockStates.block(selected.iterator().next());

                if (block != null)
                {
                    this.pick(row, block.getDefaultState());
                }
            }

            BaseScreen.openScreen(this);
        });
        screen.setHasSearch(true);
        screen.setTitle("litematica_upgraded.title.screen.missing_blocks.pick", MissingBlockGuesses.displayName(row.name));
        screen.setParent(this);
        BaseScreen.openScreen(screen);
    }

    @Override
    protected void reAddActiveWidgets()
    {
        super.reAddActiveWidgets();

        this.addWidget(this.useTheseButton);
        this.addWidget(this.leaveOutButton);
        this.addWidget(this.guessesButton);
        this.addWidget(this.skipButton);
    }

    @Override
    protected void updateWidgetPositions()
    {
        super.updateWidgetPositions();

        int y = this.getBottom() - 24;
        this.useTheseButton.setPosition(12, y);
        this.leaveOutButton.setPosition(this.useTheseButton.getRight() + 4, y);
        this.guessesButton.setPosition(this.leaveOutButton.getRight() + 4, y);
        this.skipButton.setPosition(this.guessesButton.getRight() + 4, y);
    }

    @Override
    protected DataListWidget<Row> createListWidget()
    {
        DataListWidget<Row> listWidget = new DataListWidget<>(this::getRows, false);
        listWidget.setListEntryWidgetFixedHeight(22);
        listWidget.setDataListEntryWidgetFactory(RowWidget::new);
        return listWidget;
    }

    /** What a row shows for a block: its name, or "left out" for air. */
    public static String blockName(IBlockState state)
    {
        if (state.getBlock() == Blocks.AIR)
        {
            return StringUtils.translate("litematica_upgraded.gui.label.missing.left_out");
        }

        return state.getBlock().getLocalizedName();
    }

    protected class RowWidget extends BaseDataListEntryWidget<Row>
    {
        protected final GenericButton changeButton;

        public RowWidget(Row data, DataListEntryWidgetData constructData)
        {
            super(data, constructData);

            this.changeButton = GenericButton.create("litematica_upgraded.gui.button.missing.change", () -> MissingBlocksScreen.this.openPicker(data));
            String color = data.picked == data.loaded ? "" : "§e";
            this.setText(StyledTextLine.parseFirstLine(MissingBlockGuesses.displayName(data.name) + " §7x" + data.count
                                                       + "§r  ->  " + color + blockName(data.picked)));
            this.getBackgroundRenderer().getNormalSettings().setColor(this.isOdd ? 0xA0101010 : 0xA0303030);
            this.getBackgroundRenderer().getHoverSettings().setColor(0xA0707070);
        }

        @Override
        public void reAddSubWidgets()
        {
            super.reAddSubWidgets();

            this.addWidget(this.changeButton);
        }

        @Override
        public void updateSubWidgetPositions()
        {
            super.updateSubWidgetPositions();

            this.changeButton.setRight(this.getRight() - 2);
            this.changeButton.setY(this.getY() + 1);
        }
    }
}
