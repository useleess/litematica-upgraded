package com.useleess.litematicaupgraded.selftest;

import java.util.function.Consumer;

import net.minecraft.block.Block;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import com.useleess.litematicaupgraded.litematica.data.DataManager;
import com.useleess.litematicaupgraded.malilib.config.value.LayerMode;
import com.useleess.litematicaupgraded.malilib.util.position.LayerRange;
import com.useleess.litematicaupgraded.printer.ContainerGrab;
import com.useleess.litematicaupgraded.printer.Printer;
import com.useleess.litematicaupgraded.printer.PrinterConfigs;

/**
 * grab/: Grab From Containers on a server without any mod. Each test sets a storage block beside the placement by
 * command, opens it the way a player does, and reads the real inventory and the real slots.
 */
final class GrabTests {
    private static final BlockPos STORE = new BlockPos(-1, 0, 2);
    /** Ticks with the screen open before the verdict: longer than the count, a few stacks and the job's idle end. */
    private static final int GRAB_TICKS = 45;

    private GrabTests() {}

    /** What the test finds with the storage screen still open; null when all is right. */
    private interface Verdict {
        String wrong(Runner.Context c);
    }

    private static int carried(Runner.Context c, Item item) {
        int count = 0;
        for (ItemStack stack : c.mc.player.inventory.mainInventory) if (stack.getItem() == item) count += stack.getCount();
        return count;
    }

    private static int stored(Runner.Context c, Item item) {
        int count = 0;
        for (Slot slot : c.mc.player.openContainer.inventorySlots) {
            if (slot.inventory != c.mc.player.inventory && slot.getStack().getItem() == item) count += slot.getStack().getCount();
        }
        return count;
    }

    private static String counts(Runner.Context c, Item item, int carried, int stored) {
        int have = carried(c, item), left = stored(c, item);
        return have == carried && left == stored ? null
            : item.getRegistryName() + ": carried " + have + " (wanted " + carried + "), left in the container " + left + " (wanted " + stored + "); job: " + ContainerGrab.lastEnd;
    }

    /** The chest screen with the job's message on it. */
    private static void shot(Runner.Context c) {
        try {
            Runner.shot(c.mc, "grab-took-items");
        } catch (Exception e) {
            throw new AssertionError("no screenshot: " + e, e);
        }
    }

    private static boolean screenOpen(Runner.Context c) {
        return c.mc.currentScreen instanceof GuiContainer && c.mc.player.openContainer != c.mc.player.inventoryContainer;
    }

    private static void open(Runner.Context c, BlockPos at) {
        c.mc.playerController.processRightClickBlock(c.mc.player, c.mc.world, at, EnumFacing.UP, new Vec3d(at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5), EnumHand.MAIN_HAND);
    }

    private static NBTTagCompound item(int slot, String id, int count) {
        NBTTagCompound item = new NBTTagCompound();
        item.setByte("Slot", (byte) slot);
        item.setString("id", id);
        item.setByte("Count", (byte) count);
        return item;
    }

    /** The command setting the storage block, holding full stacks: item id, number of stacks, slot after slot (short ids: a chat line holds 256 characters). */
    private static String setStorage(Scenario s, String block, Object... items) {
        NBTTagList list = new NBTTagList();
        int slot = 0;
        for (int i = 0; i < items.length; i += 2) {
            for (int n = 0; n < (Integer) items[i + 1]; n++) list.appendTag(item(slot++, (String) items[i], 64));
        }
        NBTTagCompound tile = new NBTTagCompound();
        tile.setTag("Items", list);
        BlockPos at = s.world(STORE);
        // a shulker box facing the floor (meta 0) cannot open
        int meta = block.contains("shulker") ? EnumFacing.UP.getIndex() : 0;
        return "setblock " + at.getX() + " " + at.getY() + " " + at.getZ() + " " + block + " " + meta + " replace " + tile;
    }

    /** Opens the storage block once it stands and gives the verdict when the job had time to end. */
    private static Scenario.Check afterOpening(String block, Verdict verdict) {
        int[] phase = {0, 0};
        String[] result = {null};
        return (c, s) -> {
            BlockPos at = s.world(STORE);
            if (phase[0] == 0) {
                if (c.mc.world.getBlockState(at).getBlock() != Block.getBlockFromName(block)) return "waiting for the storage block";
                open(c, at);
                phase[0] = 1;
                return "opening it";
            }
            if (phase[0] == 1) {
                if (!screenOpen(c)) {
                    if (++phase[1] > 80) throw new AssertionError("the storage screen did not open");
                    return "waiting for the screen";
                }
                if (++phase[1] < GRAB_TICKS) return "waiting for the grab";
                result[0] = verdict.wrong(c);
                c.mc.player.closeScreen();
                phase[0] = 2;
                return "read";
            }
            return result[0];
        };
    }

    /** A test on the platform: the printer stays off and the player starts empty. */
    private static Scenario scenario(String name, boolean on, String block, Verdict verdict, Object... items) {
        Scenario s = new Scenario("grab/" + name);
        s.give = false;
        s.autoEnable = false;
        s.stable = 3;
        s.at.put(0, c -> {
            PrinterConfigs.CONTAINER_GRAB.setBooleanValue(on);
            s.command(c.mc, setStorage(s, block, items));
        });
        s.check = afterOpening(block, verdict);
        return s;
    }

    private static Scenario stone(Scenario s, int count) {
        for (int i = 0; i < count; i++) s.put(i % 10, 0, i / 10, Blocks.STONE.getDefaultState());
        return s;
    }

    static void register(Runner.Registry r) {
        Item stone = Item.getItemFromBlock(Blocks.STONE), planks = Item.getItemFromBlock(Blocks.PLANKS), dirt = Item.getItemFromBlock(Blocks.DIRT);
        String chest = "minecraft:chest", stoneId = "stone";

        Scenario layer = scenario("only-the-selected-layer-is-taken", true, chest, c -> {
            String wrong = counts(c, stone, 64, 0);
            if (wrong == null) wrong = counts(c, planks, 0, 64);
            return wrong != null ? wrong : counts(c, dirt, 0, 64);
        }, stoneId, 1, "minecraft:planks", 1, "minecraft:dirt", 1);
        layer.put(0, 0, 0, Blocks.STONE.getDefaultState()).put(0, 1, 0, Blocks.PLANKS.getDefaultState());
        Consumer<Runner.Context> setup = layer.at.get(0);
        layer.at.put(0, c -> {
            setup.accept(c);
            LayerRange layers = DataManager.getRenderLayerRange();
            layers.setLayerMode(LayerMode.SINGLE_LAYER, false);
            layers.setLayerSingle(layer.origin.getY());
        });
        ServerTests.register(r, layer);

        ServerTests.register(r, stone(scenario("whole-stacks-no-more-than-needed", true, chest, c -> {
            shot(c);
            return counts(c, stone, 128, 192);
        }, stoneId, 5), 70));

        Scenario have = stone(scenario("carried-items-count", true, chest, c -> counts(c, stone, 3, 64), stoneId, 1), 3);
        have.extraCommands.add("give @s minecraft:stone 3");
        ServerTests.register(r, have);

        Scenario built = stone(scenario("built-blocks-are-not-needed", true, chest, c -> counts(c, stone, 0, 64), stoneId, 1), 3);
        for (int i = 0; i < 3; i++) built.preplaced.put(new BlockPos(i, 0, 0), Blocks.STONE.getDefaultState());
        ServerTests.register(r, built);

        ServerTests.register(r, stone(scenario("switched-off-takes-nothing", false, chest, c -> counts(c, stone, 0, 64), stoneId, 1), 3));
        ServerTests.register(r, stone(scenario("placed-shulker-box", true, "minecraft:purple_shulker_box", c -> counts(c, stone, 64, 0), stoneId, 1), 3));
        ServerTests.register(r, stone(scenario("hopper", true, "minecraft:hopper", c -> counts(c, stone, 64, 0), stoneId, 1), 3));

        Scenario full = stone(scenario("full-inventory-stops-and-says-so", true, chest, c -> {
            String wrong = counts(c, stone, 64, 128);
            return wrong != null || "inventory full".equals(ContainerGrab.lastEnd) ? wrong : "the job ended with: " + ContainerGrab.lastEnd;
        }, stoneId, 3), 100);
        for (int i = 0; i < 35; i++) full.extraCommands.add("give @s minecraft:dirt 64");
        full.extraReady = mc -> !mc.player.inventory.mainInventory.get(34).isEmpty();
        ServerTests.register(r, full);

        printersFillIsLeftAlone(r);
        failureSwitchesOff(r);
    }

    /** The printer's own fill screen: what it puts in stays, though the schematic still wants that item elsewhere. */
    private static void printersFillIsLeftAlone(Runner.Registry r) {
        Scenario s = new Scenario("grab/printers-own-fill-is-left-alone");
        BlockPos chest = new BlockPos(0, 0, 0);
        NBTTagList items = new NBTTagList();
        items.appendTag(item(0, "minecraft:cobblestone", 5));
        NBTTagCompound tile = new NBTTagCompound();
        tile.setString("id", "minecraft:chest");
        tile.setTag("Items", items);
        s.put(0, 0, 0, Blocks.CHEST.getDefaultState());
        s.tiles.put(chest, tile);
        s.put(9, 0, 9, Blocks.COBBLESTONE.getDefaultState());
        s.range = 5;
        s.give = false;
        s.extraCommands.add("give @s minecraft:chest 1");
        s.extraCommands.add("give @s minecraft:cobblestone 5");
        s.stable = 3;
        s.at.put(0, c -> {
            PrinterConfigs.FILL_CONTAINERS.setBooleanValue(true);
            PrinterConfigs.CONTAINER_GRAB.setBooleanValue(true);
        });
        int[] phase = {0, 0};
        String[] result = {null};
        Item cobble = Item.getItemFromBlock(Blocks.COBBLESTONE);
        s.check = (c, sc) -> {
            BlockPos at = sc.world(chest);
            if (phase[0] == 0) {
                if (!Printer.FILLED.contains(at) || c.mc.currentScreen != null) return "the chest is not filled yet";
                PrinterConfigs.PRINTER_ENABLED.setBooleanValue(false);
                PrinterConfigs.CONTAINER_GRAB.setBooleanValue(false);
                open(c, at);
                phase[0] = 1;
                return "opening it";
            }
            if (phase[0] == 1) {
                if (!screenOpen(c) || ++phase[1] < 8) return "waiting for the contents";
                result[0] = counts(c, cobble, 0, 5);
                c.mc.player.closeScreen();
                phase[0] = 2;
                return "read";
            }
            return result[0];
        };
        ServerTests.register(r, s);
    }

    /** A container that breaks while the job reads it: the switch goes off and the game goes on. */
    private static void failureSwitchesOff(Runner.Registry r) {
        Scenario s = stone(new Scenario("grab/failure-switches-it-off"), 3);
        s.give = false;
        s.autoEnable = false;
        s.stable = 3;
        s.at.put(0, c -> {
            PrinterConfigs.CONTAINER_GRAB.setBooleanValue(true);
            s.command(c.mc, setStorage(s, "minecraft:chest", "minecraft:stone", 1));
        });
        int[] phase = {0};
        s.check = (c, sc) -> {
            BlockPos at = sc.world(STORE);
            if (phase[0] == 0) {
                if (c.mc.world.getBlockState(at).getBlock() != Blocks.CHEST) return "waiting for the chest";
                open(c, at);
                phase[0] = 1;
                return "opening it";
            }
            if (phase[0] == 1) {
                if (!screenOpen(c)) return "waiting for the screen";
                int window = c.mc.player.openContainer.windowId;
                c.mc.player.openContainer = new ContainerChest(c.mc.player.inventory, new InventoryBasic("broken", false, 27), c.mc.player) {
                    @Override
                    public Slot getSlot(int index) {
                        throw new IllegalStateException("broken on purpose by the test");
                    }
                };
                c.mc.player.openContainer.windowId = window;
                phase[0] = 2;
                return "broke it";
            }
            if (PrinterConfigs.CONTAINER_GRAB.getBooleanValue()) return "the switch is still on";
            if (phase[0] == 2) {
                c.mc.player.closeScreen();
                phase[0] = 3;
            }
            return c.mc.currentScreen == null ? null : "the screen is still open";
        };
        ServerTests.register(r, s);
    }
}
