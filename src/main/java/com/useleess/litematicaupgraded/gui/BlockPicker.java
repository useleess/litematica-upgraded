package com.useleess.litematicaupgraded.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.block.Block;

import com.useleess.litematicaupgraded.convert.BlockStates;
import com.useleess.litematicaupgraded.malilib.gui.BaseScreen;
import com.useleess.litematicaupgraded.malilib.gui.StringListSelectionScreen;

/** Picks a block by its name from a searchable list of every block (malilib's string list screen), then goes back to the parent screen. */
public final class BlockPicker
{
    private BlockPicker() {}

    /** Every block name of the game, air first when it is offered (the list sorts itself). */
    static List<String> names(boolean withAir)
    {
        List<String> names = new ArrayList<>();

        if (withAir)
        {
            names.add("minecraft:air");
        }

        for (Object key : Block.REGISTRY.getKeys())
        {
            String name = key.toString();

            if (name.equals("minecraft:air") == false)
            {
                names.add(name);
            }
        }

        return names;
    }

    /**
     * Opens the picker over the parent screen; the picked block goes to the consumer, then the parent is shown again.
     * @param titleKey the screen title's translation key, with one %s for the argument
     */
    public static void open(BaseScreen parent, boolean withAir, String titleKey, String titleArg, Consumer<Block> picked)
    {
        StringListSelectionScreen screen = new StringListSelectionScreen(names(withAir), selected -> {
            if (selected.isEmpty() == false)
            {
                Block block = BlockStates.block(selected.iterator().next());

                if (block != null)
                {
                    picked.accept(block);
                }
            }

            BaseScreen.openScreen(parent);
        });
        screen.setHasSearch(true);
        screen.setTitle(titleKey, titleArg);
        screen.setParent(parent);
        BaseScreen.openScreen(screen);
    }
}
