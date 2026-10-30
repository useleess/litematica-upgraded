package com.useleess.litematicaupgraded.selftest;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.BlockRedstoneComparator;
import net.minecraft.block.BlockRedstoneRepeater;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.BlockStoneSlab;
import net.minecraft.block.BlockLog;
import net.minecraft.block.BlockOldLog;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.CPacketPlayerDigging;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import com.useleess.litematicaupgraded.printer.Printer;
import com.useleess.litematicaupgraded.printer.PrinterConfigs;
import com.useleess.litematicaupgraded.printer.PrinterRange;
import com.useleess.litematicaupgraded.printer.ServerAssist;
import com.useleess.litematicaupgraded.printer.ServerFeaturesClient;
import com.useleess.litematicaupgraded.server.ServerFeatures;
import com.useleess.litematicaupgraded.server.ShulkerRestock;

/**
 * server/: the server part of the printer. The "forge" phase joins a Forge dedicated server that has the mod
 * (server/load, admin, accurate, air, range, shulker); server/fallback runs in the "plain" phase, on a server without
 * the mod. The non-op test comes last: it takes the test player's operator rights away.
 */
final class ServerTests {
    private ServerTests() {}

    private static final IBlockState STONE = PrinterTests.STONE;
    private static final IBlockState GATE = Blocks.OAK_FENCE_GATE.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.NORTH);

    /** The chat lines the server sent (command replies, refusals), as text. */
    static final List<String> CHAT = new CopyOnWriteArrayList<>();

    public static final class ChatListener {
        @SubscribeEvent
        public void onChat(ClientChatReceivedEvent event) {
            CHAT.add(event.getMessage().getUnformattedText());
        }
    }

    static {
        MinecraftForge.EVENT_BUS.register(new ChatListener());
    }

    static boolean chatHas(String text) {
        for (String line : CHAT) if (line.toLowerCase().contains(text.toLowerCase())) return true;
        return false;
    }

    private static void send(Runner.Context c, String command) {
        c.mc.player.sendChatMessage("/" + command);
    }

    private static void register(Runner.Registry r, Scenario s) {
        r.waitFor(s.id, s.maxTicks + 300, s::start, s::poll);
    }

    private static int[] counters = new int[4];

    /** Items of a kind in the inventory (boxes not counted) and inside the carried shulker boxes. */
    private static int carried(Minecraft mc, Item item) {
        int total = 0;
        for (ItemStack stack : mc.player.inventory.mainInventory) if (stack.getItem() == item) total += stack.getCount();
        return total;
    }

    private static int inBoxes(Minecraft mc, Item item) {
        int total = 0;
        for (ItemStack stack : mc.player.inventory.mainInventory) {
            if (!ShulkerRestock.isBox(stack)) continue;
            NonNullList<ItemStack> contents = ShulkerRestock.contents(stack);
            for (ItemStack inner : contents) if (inner.getItem() == item) total += inner.getCount();
        }
        return total;
    }

    private static boolean hasBoxes(Minecraft mc, int wanted) {
        int boxes = 0;
        for (ItemStack stack : mc.player.inventory.mainInventory) if (ShulkerRestock.isBox(stack)) boxes++;
        return boxes >= wanted;
    }

    private static String box(String id, int count) {
        return "give @s minecraft:white_shulker_box 1 0 {BlockEntityTag:{Items:[{Slot:0b,id:\"minecraft:" + id + "\",Count:" + count + "b,Damage:0s}]}}";
    }

    /** A placement test: the server part does the work, so the client's own fake rotation and position are off. */
    private static Scenario accurate(String name) {
        Scenario s = new Scenario("server/accurate/" + name);
        s.autoEnable = false;
        int[] intents = {0};
        s.at.put(0, c -> {
            intents[0] = ServerAssist.intentsSent;
            PrinterConfigs.FAKE_ROTATION.setBooleanValue(false);
            PrinterConfigs.FAKE_POSITION.setBooleanValue(false);
            PrinterConfigs.PRINTER_ENABLED.setBooleanValue(true);
        });
        s.check = (c, sc) -> {
            String problem = Scenario.allBuilt(c, sc);
            if (problem != null) return problem;
            return ServerAssist.intentsSent > intents[0] ? null : "no placement intent was sent: the server part did not do the work";
        };
        return s;
    }

    static void register(Runner.Registry r) {
        // server/load/: the client sees the server part (the server log is judged by tools/ingame.ps1)
        r.add("server/load/client-sees-the-server-part", c -> {
            c.check(ServerFeatures.received, "no capabilities message arrived from the server");
            c.check(ServerFeaturesClient.last != null, "the client applied no capabilities");
            c.check(ServerFeatures.printerAllowed && ServerFeatures.accuratePlacement && ServerFeatures.airPlacement && ServerFeatures.shulkerRestock,
                "the server defaults are not all on: " + ServerFeaturesClient.last);
            c.check(ServerFeatures.range == 16, "the server range is " + ServerFeatures.range + ", the default is 16");
        });

        // server/admin/: the admin switch
        r.waitFor("server/admin/command-off-stops-the-running-printer", 200, c -> {
            CHAT.clear();
            c.check(ServerFeatures.printerAllowed, "the printer is not allowed at the start");
            PrinterConfigs.PRINTER_ENABLED.setBooleanValue(true);
            c.check(Printer.INSTANCE.isEnabled(), "the printer did not turn on while the server allows it");
            counters[0] = ServerFeaturesClient.blocked;
            send(c, "litematicaupgraded printer off");
        }, c -> {
            if (ServerFeatures.printerAllowed) return false;
            c.check(!PrinterConfigs.PRINTER_ENABLED.getBooleanValue(), "the server switched the printer off but the client printer is still on");
            c.check(!Printer.INSTANCE.isEnabled(), "the client printer still runs");
            c.check(ServerFeaturesClient.blocked == counters[0] + 1, "the player was not told once (" + ServerFeaturesClient.blocked + ")");
            c.check(ServerFeaturesClient.lastNotice.contains("does not allow"), "the notice is not the translated text: '" + ServerFeaturesClient.lastNotice + "'");
            return chatHas("switched off for all players");
        });
        r.waitFor("server/admin/printer-cannot-be-turned-on-while-off", 100, c -> {
            c.check(!ServerFeatures.printerAllowed, "the printer is allowed, the previous test should have switched it off");
            counters[0] = ServerFeaturesClient.blocked;
            PrinterConfigs.PRINTER_ENABLED.setBooleanValue(true); // what the hotkey does
            c.check(!Printer.INSTANCE.isEnabled(), "the printer counts as running right after the toggle although the server forbids it");
        }, c -> {
            if (PrinterConfigs.PRINTER_ENABLED.getBooleanValue()) return false;
            c.check(ServerFeaturesClient.blocked == counters[0] + 1, "the toggle was not refused with a notice (" + ServerFeaturesClient.blocked + ")");
            c.check(Printer.PLACED.isEmpty() || !Printer.INSTANCE.isEnabled(), "the printer ran");
            return true;
        });
        int[] on = {0, 0};
        r.waitFor("server/admin/command-on-allows-the-printer-again", 300, c -> {
            on[0] = 0;
            on[1] = 0;
            CHAT.clear();
            send(c, "litematicaupgraded printer on");
        }, c -> {
            switch (on[0]) {
                case 0:
                    if (!ServerFeatures.printerAllowed) return false;
                    PrinterConfigs.PRINTER_ENABLED.setBooleanValue(true);
                    on[0] = 1;
                    return false;
                case 1:
                    if (++on[1] < 10) return false;
                    c.check(PrinterConfigs.PRINTER_ENABLED.getBooleanValue() && Printer.INSTANCE.isEnabled(), "the printer did not stay on after the server allowed it again");
                    PrinterConfigs.PRINTER_ENABLED.setBooleanValue(false);
                    CHAT.clear();
                    send(c, "litematicaupgraded printer status");
                    on[0] = 2;
                    return false;
                default:
                    if (!chatHas("printer is allowed")) return false;
                    c.check(!chatHas("switched off"), "the status says the printer is off");
                    return true;
            }
        });
        // Easy Place is not touched by the switch: the printer is switched off, the block is still placed by Easy Place
        Scenario ep = new Scenario("server/admin/easy-place-works-while-the-printer-is-off");
        ep.autoEnable = false;
        ep.put(0, 0, 0, STONE);
        boolean[] restored = {false};
        ep.at.put(0, c -> send(c, "litematicaupgraded printer off"));
        ep.check = (c, sc) -> {
            if (restored[0]) return ServerFeatures.printerAllowed ? null : "waiting for the printer to be allowed again";
            if (ServerFeatures.printerAllowed) return "waiting for the switch";
            String problem = Scenario.allBuilt(c, sc);
            if (problem != null) {
                PrinterTests.clickEasyPlace(c, sc, 0, 0, 0);
                return problem;
            }
            restored[0] = true;
            send(c, "litematicaupgraded printer on");
            return "switching the printer on again";
        };
        register(r, ep);

        // server/accurate/: facing, half, axis, delay and mode in one click, set by the server
        Scenario a1 = accurate("stairs-facing-and-half");
        a1.put(0, 0, 0, Blocks.STONE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.WEST).withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.TOP));
        register(r, a1);
        Scenario a2 = accurate("piston-facing-down-on-the-ground");
        a2.put(0, 0, 0, PrinterTests.facing(Blocks.PISTON, EnumFacing.DOWN));
        register(r, a2);
        Scenario a3 = accurate("repeater-delay-and-comparator-mode-without-extra-clicks");
        a3.put(0, 0, 0, Blocks.UNPOWERED_REPEATER.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.WEST).withProperty(BlockRedstoneRepeater.DELAY, 4));
        a3.put(2, 0, 0, Blocks.UNPOWERED_COMPARATOR.getDefaultState().withProperty(BlockHorizontal.FACING, EnumFacing.SOUTH)
            .withProperty(BlockRedstoneComparator.MODE, BlockRedstoneComparator.Mode.SUBTRACT));
        register(r, a3);
        Scenario a4 = accurate("slab-top-half-and-log-axis");
        a4.put(0, 0, 0, Blocks.STONE_SLAB.getDefaultState().withProperty(BlockSlab.HALF, BlockSlab.EnumBlockHalf.TOP).withProperty(BlockStoneSlab.VARIANT, BlockStoneSlab.EnumType.STONE));
        a4.put(2, 0, 0, Blocks.LOG.getDefaultState().withProperty(BlockOldLog.VARIANT, net.minecraft.block.BlockPlanks.EnumType.OAK).withProperty(BlockLog.LOG_AXIS, BlockLog.EnumAxis.X));
        register(r, a4);

        // server/air/: a fence gate over nothing, placed by the server, no scaffold block
        Scenario air = new Scenario("server/air/fence-gate-over-air-placed-directly");
        air.put(0, 1, 0, GATE);
        int[] airBefore = {0};
        boolean[] scaffold = {false};
        air.at.put(0, c -> airBefore[0] = ServerAssist.airSent);
        air.check = (c, sc) -> {
            if (!Scenario.isAir(c, sc, new BlockPos(0, 0, 0))) scaffold[0] = true;
            String problem = Scenario.allBuilt(c, sc);
            if (problem != null) return problem;
            if (scaffold[0]) return "a block was placed under the gate";
            return ServerAssist.airSent > airBefore[0] ? null : "the gate stands but no air placement request was sent";
        };
        register(r, air);

        // server/range/: clicks beyond the vanilla reach, checked by the server
        r.add("server/range/printer-range-follows-the-server", c -> {
            int before = PrinterConfigs.RANGE.getIntegerValue();
            PrinterConfigs.RANGE.setIntegerValue(8);
            try {
                c.check(PrinterRange.cap() == 16, "the cap is " + PrinterRange.cap() + ", the server allows 16");
                c.check(PrinterRange.printer() == 8, "the printer range is " + PrinterRange.printer() + ", the setting is 8");
                c.check(PrinterRange.mining(8) == 8, "the mining range is " + PrinterRange.mining(8));
            } finally {
                PrinterConfigs.RANGE.setIntegerValue(before);
            }
        });
        Scenario near = rangeScenario("click-beyond-the-vanilla-reach-is-performed", 0);
        near.put(0, 0, 0, STONE);
        near.check = (c, sc) -> {
            if (sc.sinceReady() < 15) return "waiting for the click to arrive";
            return Scenario.isAir(c, sc, new BlockPos(0, 0, 0)) ? "the server did not place the block" : null;
        };
        register(r, near);
        Scenario far = rangeScenario("click-beyond-the-server-maximum-is-refused", 25);
        far.put(0, 0, 0, STONE);
        far.check = (c, sc) -> {
            if (sc.sinceReady() < 25) return "waiting for the server's answer";
            if (PrinterRange.rangedClicksSent == counters[1]) return "no ranged click was sent: the test did not try";
            BlockPos target = sc.world(new BlockPos(0, 0, -25));
            return c.mc.world.getBlockState(target).getBlock() == Blocks.AIR ? null : "the server placed a block 35 blocks away";
        };
        register(r, far);
        Scenario build = new Scenario("server/range/printer-builds-at-the-full-range");
        build.range = 8;
        build.standZ = 7.5;
        build.put(0, 0, 0, STONE).put(2, 0, 0, STONE).put(1, 0, 1, STONE);
        build.stable = 15;
        register(r, build);
        Scenario dig = new Scenario("server/range/dig-beyond-the-vanilla-reach-breaks-a-block");
        dig.autoEnable = false;
        dig.standZ = 10.5;
        dig.put(0, 0, 0, STONE);
        dig.preplaced.put(new BlockPos(0, 0, 0), STONE);
        int[] digs = {0};
        dig.at.put(0, c -> {
            digs[0] = PrinterRange.rangedDigsSent;
            dig.command(c.mc, "gamemode creative");
        });
        dig.check = (c, sc) -> {
            if (!c.mc.playerController.getCurrentGameType().isCreative()) return "waiting for creative mode";
            BlockPos at = sc.world(new BlockPos(0, 0, 0));
            if (PrinterRange.rangedDigsSent == digs[0]) {
                PrinterRange.dig(c.mc.player, CPacketPlayerDigging.Action.START_DESTROY_BLOCK, at, EnumFacing.UP);
                return "dig sent";
            }
            return Scenario.isAir(c, sc, new BlockPos(0, 0, 0)) ? null : "the block 10 blocks away is still there";
        };
        register(r, dig);

        // server/shulker/: items out of shulker boxes in the inventory
        Scenario s1 = new Scenario("server/shulker/printer-takes-missing-blocks-from-a-shulker-box");
        s1.give = false;
        s1.put(0, 0, 0, STONE).put(1, 0, 0, STONE).put(2, 0, 0, STONE).put(3, 0, 0, STONE);
        s1.extraCommands.add(box("stone", 8));
        s1.extraReady = mc -> hasBoxes(mc, 1);
        int[] shulker = {0};
        s1.at.put(0, c -> shulker[0] = ServerAssist.shulkerSent);
        s1.check = (c, sc) -> {
            String problem = Scenario.allBuilt(c, sc);
            if (problem != null) return problem;
            int left = carried(c.mc, Item.getItemFromBlock(Blocks.STONE)) + inBoxes(c.mc, Item.getItemFromBlock(Blocks.STONE));
            if (left != 4) return "stone left over: " + left + ", wanted 4 (8 in the box, 4 placed)";
            return ServerAssist.shulkerSent > shulker[0] ? null : "no restock request was sent";
        };
        register(r, s1);
        Scenario s2 = new Scenario("server/shulker/several-boxes-and-item-kinds");
        s2.give = false;
        s2.put(0, 0, 0, STONE).put(1, 0, 0, STONE).put(2, 0, 0, Blocks.DIRT.getDefaultState()).put(3, 0, 0, Blocks.DIRT.getDefaultState());
        s2.extraCommands.add(box("stone", 5));
        s2.extraCommands.add(box("dirt", 5));
        s2.extraReady = mc -> hasBoxes(mc, 2);
        s2.check = (c, sc) -> {
            String problem = Scenario.allBuilt(c, sc);
            if (problem != null) return problem;
            int stone = carried(c.mc, Item.getItemFromBlock(Blocks.STONE)) + inBoxes(c.mc, Item.getItemFromBlock(Blocks.STONE));
            int dirt = carried(c.mc, Item.getItemFromBlock(Blocks.DIRT)) + inBoxes(c.mc, Item.getItemFromBlock(Blocks.DIRT));
            return stone == 3 && dirt == 3 ? null : "left over: stone " + stone + " (wanted 3), dirt " + dirt + " (wanted 3)";
        };
        register(r, s2);
        Scenario s3 = new Scenario("server/shulker/box-left-alone-when-nothing-is-missing");
        s3.put(0, 0, 0, STONE).put(1, 0, 0, STONE);
        s3.extraCommands.add(box("dirt", 8));
        s3.extraReady = mc -> hasBoxes(mc, 1);
        int[] shulkerBefore = {0};
        s3.at.put(0, c -> shulkerBefore[0] = ServerAssist.shulkerSent);
        s3.stable = 30;
        s3.check = (c, sc) -> {
            String problem = Scenario.allBuilt(c, sc);
            if (problem != null) return problem;
            if (ServerAssist.shulkerSent != shulkerBefore[0]) return "a restock was asked for although nothing was missing";
            return inBoxes(c.mc, Item.getItemFromBlock(Blocks.DIRT)) == 8 && carried(c.mc, Item.getItemFromBlock(Blocks.DIRT)) == 0 ? null : "the box was emptied";
        };
        register(r, s3);

        // server/fallback/ (plain phase, a server without the mod)
        r.add("server/fallback/every-server-feature-reports-off", c -> {
            c.check(!ServerFeatures.received, "a capabilities message arrived from a server without the mod");
            c.check(ServerFeatures.printerAllowed, "the printer is not allowed without a server part");
            c.check(!ServerFeatures.accuratePlacement && !ServerFeatures.airPlacement && !ServerFeatures.shulkerRestock && ServerFeatures.range == 0,
                "a server feature reports on");
            c.check(!ServerAssist.accurate(Blocks.PISTON) && !ServerAssist.air(GATE), "the printer would use a server feature");
            int before = PrinterConfigs.RANGE.getIntegerValue();
            PrinterConfigs.RANGE.setIntegerValue(8);
            try {
                c.check(PrinterRange.cap() == 6, "the cap is " + PrinterRange.cap() + ", the vanilla cap is 6");
                c.check(PrinterRange.printer() == 6, "the printer range is " + PrinterRange.printer() + ", the vanilla reach keeps it at 6");
                c.check(PrinterRange.mining(8) == 5.5, "the mining range is " + PrinterRange.mining(8));
            } finally {
                PrinterConfigs.RANGE.setIntegerValue(before);
            }
        });
        Scenario fb = new Scenario("server/fallback/printer-still-works-client-only");
        fb.put(0, 0, 0, Blocks.STONE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.WEST).withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.TOP));
        fb.put(1, 0, 0, STONE);
        fb.put(2, 0, 0, PrinterTests.facing(Blocks.PISTON, EnumFacing.EAST));
        fb.put(4, 1, 0, GATE);
        fb.extraCommands.add("give @s minecraft:dirt 3");
        int[][] sent = {{0, 0, 0}};
        fb.at.put(0, c -> sent[0] = new int[] {ServerAssist.intentsSent, ServerAssist.airSent, ServerAssist.shulkerSent});
        fb.maxTicks = 700;
        fb.check = (c, sc) -> {
            String problem = Scenario.allBuilt(c, sc);
            if (problem != null) return problem;
            boolean quiet = sent[0][0] == ServerAssist.intentsSent && sent[0][1] == ServerAssist.airSent && sent[0][2] == ServerAssist.shulkerSent;
            return quiet ? null : "the printer sent a server-part request";
        };
        register(r, fb);

        // the last test: the switch is off; the test player joins again (a fresh connection: the server's chat spam counter of
        // a long test run would kick a player the moment the operator rights are gone), is told the switch is still off,
        // loses the operator rights and is refused when switching the printer on; the switch stays as it was
        int[] nonOp = {0, 0};
        r.waitFor("server/admin/non-op-is-refused-and-the-state-stays", 800, c -> {
            nonOp[0] = 0;
            nonOp[1] = 0;
            CHAT.clear();
            send(c, "litematicaupgraded printer off");
        }, c -> {
            switch (nonOp[0]) {
                case 0:
                    if (ServerFeatures.printerAllowed) return false;
                    if (++nonOp[1] < 10) return false;
                    c.mc.world.sendQuittingDisconnectingPacket();
                    c.mc.loadWorld(null);
                    c.mc.displayGuiScreen(new GuiMainMenu());
                    nonOp[0] = 1;
                    nonOp[1] = 0;
                    return false;
                case 1:
                    if (++nonOp[1] < 40) return false;
                    FMLClientHandler.instance().connectToServer(new GuiMainMenu(), new ServerData("selftest", System.getProperty("litematicaupgraded.selftest.join", ""), false));
                    nonOp[0] = 2;
                    nonOp[1] = 0;
                    return false;
                case 2:
                    // in the world again, and the server told this new connection that the printer is off
                    if (c.mc.player == null || c.mc.world == null || !ServerFeatures.received) return false;
                    c.check(!ServerFeatures.printerAllowed, "the server did not tell the new connection that the printer is off");
                    if (++nonOp[1] == 20) send(c, "gamemode creative");
                    if (nonOp[1] < 40) return false;
                    send(c, "deop " + c.mc.player.getName());
                    nonOp[0] = 3;
                    nonOp[1] = 0;
                    return false;
                case 3:
                    if (++nonOp[1] < 20) return false;
                    CHAT.clear();
                    send(c, "litematicaupgraded printer on");
                    nonOp[0] = 4;
                    nonOp[1] = 0;
                    return false;
                default:
                    if (!chatHas("permission")) return false;
                    if (++nonOp[1] < 40) return false;
                    c.check(!ServerFeatures.printerAllowed, "a player without operator rights switched the printer on");
                    return true;
            }
        });
    }

    /**
     * A player standing 10 blocks from the platform's edge, holding stone, clicking the top of the platform block at the
     * relative z (the schematic's block 0,0,0 is then placed from far away). Counters are reset when the click is sent.
     */
    private static Scenario rangeScenario(String name, int farBehind) {
        Scenario s = new Scenario("server/range/" + name);
        s.autoEnable = false;
        s.standZ = 10.5;
        if (farBehind > 0) {
            // a stone block to click, far behind the schematic
            s.at.put(0, c -> {
                BlockPos far = s.world(new BlockPos(0, -1, -farBehind));
                s.command(c.mc, "fill " + far.getX() + " " + far.getY() + " " + far.getZ() + " " + far.getX() + " " + far.getY() + " " + far.getZ() + " minecraft:stone");
            });
        }
        s.at.put(6, c -> {
            counters[1] = PrinterRange.rangedClicksSent;
            for (int slot = 0; slot < 9; slot++) {
                if (c.mc.player.inventory.getStackInSlot(slot).getItem() == Item.getItemFromBlock(Blocks.STONE)) c.mc.player.inventory.currentItem = slot;
            }
            BlockPos support = farBehind > 0 ? s.world(new BlockPos(0, -1, -farBehind)) : s.world(new BlockPos(0, -1, 0));
            Vec3d hit = new Vec3d(support.getX() + 0.5, support.getY() + 1, support.getZ() + 0.5);
            PrinterRange.click(c.mc.player, support, EnumFacing.UP, hit, EnumHand.MAIN_HAND);
        });
        return s;
    }
}
