package com.useleess.litematicaupgraded.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.block.BlockColored;
import net.minecraft.block.BlockDirt;
import net.minecraft.block.BlockEndPortalFrame;
import net.minecraft.block.BlockFenceGate;
import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.BlockLever;
import net.minecraft.block.BlockPistonBase;
import net.minecraft.block.BlockRailPowered;
import net.minecraft.block.BlockTrapDoor;
import net.minecraft.block.BlockRedstoneRepeater;
import net.minecraft.block.BlockStairs;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.item.EnumDyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import org.junit.BeforeClass;
import org.junit.Test;

import com.useleess.litematicaupgraded.printer.PrinterRange;
import com.useleess.litematicaupgraded.server.net.MessageAirPlace;
import com.useleess.litematicaupgraded.server.net.MessageCapabilities;
import com.useleess.litematicaupgraded.server.net.MessagePlacementIntent;
import com.useleess.litematicaupgraded.server.net.MessageRangedClick;
import com.useleess.litematicaupgraded.server.net.MessageRangedDig;
import com.useleess.litematicaupgraded.server.net.MessageShulkerTake;

/** The server part's pure logic: messages, state strings, the accurate placement merge, the server options, the capabilities held by the client. */
public class ServerPartTest {
    @BeforeClass
    public static void bootstrap() {
        Bootstrap.register();
    }

    private static ByteBuf encode(net.minecraftforge.fml.common.network.simpleimpl.IMessage message) {
        ByteBuf buffer = Unpooled.buffer();
        message.toBytes(buffer);
        return buffer;
    }

    @Test
    public void capabilitiesSurviveTheWire() {
        MessageCapabilities sent = new MessageCapabilities(false, true, false, true, 12.5f);
        MessageCapabilities read = new MessageCapabilities();
        read.fromBytes(encode(sent));
        assertEquals(sent, read);
        assertFalse(read.printerAllowed);
        assertEquals(12.5f, read.range, 0);
        sent.accurateStates = true;
        read.fromBytes(encode(sent));
        assertTrue(read.accurateStates);
    }

    @Test
    public void anOlderServerPartsCapabilitiesHaveNoFullStates() {
        // what 0.2.x and Useleess Carpet send: everything up to the upload size
        ByteBuf old = Unpooled.buffer().writeBoolean(true).writeBoolean(true).writeBoolean(false).writeBoolean(true).writeFloat(0)
            .writeBoolean(false).writeBoolean(false).writeInt(0);
        MessageCapabilities read = new MessageCapabilities();
        read.fromBytes(old);
        assertTrue(read.accuratePlacement);
        assertFalse(read.accurateStates);
    }

    @Test
    public void aShortCapabilitiesMessageReadsAsOff() {
        MessageCapabilities read = new MessageCapabilities();
        read.fromBytes(Unpooled.buffer().writeBoolean(true));
        assertTrue(read.printerAllowed);
        assertFalse(read.accuratePlacement || read.airPlacement || read.shulkerRestock);
        assertEquals(0f, read.range, 0);
        MessageCapabilities empty = new MessageCapabilities();
        empty.fromBytes(Unpooled.buffer());
        assertFalse(empty.printerAllowed);
    }

    @Test
    public void placementIntentAndAirPlaceSurviveTheWire() {
        MessagePlacementIntent intent = new MessagePlacementIntent(-5, 70, 123456, "minecraft:piston[extended=false,facing=down]");
        MessagePlacementIntent read = new MessagePlacementIntent();
        read.fromBytes(encode(intent));
        assertEquals(-5, read.x);
        assertEquals(70, read.y);
        assertEquals(123456, read.z);
        assertEquals(intent.state, read.state);
        MessageAirPlace air = new MessageAirPlace(new BlockPos(1, 2, 3), "minecraft:fence_gate[facing=north]");
        MessageAirPlace airRead = new MessageAirPlace();
        airRead.fromBytes(encode(air));
        assertEquals(air.state, airRead.state);
        assertEquals(3, airRead.z);
    }

    @Test
    public void aBadStateLengthIsRefused() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(0).writeInt(0).writeInt(0).writeShort(5000);
        try {
            new MessagePlacementIntent().fromBytes(buffer);
            fail("an oversized state string was accepted");
        } catch (IllegalArgumentException expected) {
            // refused
        }
        ByteBuf lying = Unpooled.buffer();
        lying.writeInt(0).writeInt(0).writeInt(0).writeShort(10).writeBytes(new byte[] {1, 2, 3});
        try {
            new MessageAirPlace().fromBytes(lying);
            fail("a state string longer than the message was accepted");
        } catch (IllegalArgumentException expected) {
            // refused
        }
    }

    @Test
    public void rangedClickAndDigSurviveTheWireAndRefuseJunk() {
        MessageRangedClick click = new MessageRangedClick(new BlockPos(10, 64, -3), EnumFacing.WEST, 0.25f, 0.5f, 1f);
        MessageRangedClick clickRead = new MessageRangedClick();
        clickRead.fromBytes(encode(click));
        assertEquals(-3, clickRead.z);
        assertEquals(EnumFacing.WEST.getIndex(), clickRead.side);
        assertEquals(0.25f, clickRead.hitX, 0);
        MessageRangedDig dig = new MessageRangedDig(MessageRangedDig.STOP, new BlockPos(1, 2, 3), EnumFacing.UP);
        MessageRangedDig digRead = new MessageRangedDig();
        digRead.fromBytes(encode(dig));
        assertEquals(MessageRangedDig.STOP, digRead.action);
        ByteBuf junk = encode(dig);
        junk.writeByte(0);
        try {
            new MessageRangedDig().fromBytes(junk);
            fail("trailing bytes were accepted");
        } catch (IllegalArgumentException expected) {
            // refused
        }
        ByteBuf badAction = Unpooled.buffer();
        badAction.writeByte(9).writeInt(0).writeInt(0).writeInt(0).writeByte(1);
        try {
            new MessageRangedDig().fromBytes(badAction);
            fail("an unknown dig action was accepted");
        } catch (IllegalArgumentException expected) {
            // refused
        }
    }

    @Test
    public void shulkerTakeSurvivesTheWire() {
        MessageShulkerTake take = new MessageShulkerTake(7, 13, 1, 3, 64);
        MessageShulkerTake read = new MessageShulkerTake();
        read.fromBytes(encode(take));
        assertEquals(7, read.boxSlot);
        assertEquals(13, read.innerSlot);
        assertEquals(1, read.itemId);
        assertEquals(3, read.meta);
        assertEquals(64, read.count);
    }

    @Test
    public void stateStringsRoundTrip() {
        IBlockState stairs = Blocks.STONE_STAIRS.getDefaultState().withProperty(BlockStairs.FACING, EnumFacing.WEST).withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.TOP);
        String text = StateStrings.toString(stairs);
        assertTrue(text, text.startsWith("minecraft:stone_stairs[") && text.contains("facing=west") && text.contains("half=top"));
        assertEquals(stairs.getValue(BlockStairs.FACING), StateStrings.parse(text).getValue(BlockStairs.FACING));
        assertEquals(BlockStairs.EnumHalf.TOP, StateStrings.parse(text).getValue(BlockStairs.HALF));
        assertEquals("minecraft:stone", StateStrings.toString(Blocks.STONE.getDefaultState()).replaceAll("\\[.*", ""));
        assertNull(StateStrings.parse("minecraft:no_such_block[facing=north]"));
        assertNull(StateStrings.parse(""));
        // unknown properties and values keep the default
        IBlockState odd = StateStrings.parse("minecraft:stone_stairs[colour=red,facing=sideways]");
        assertEquals(Blocks.STONE_STAIRS.getDefaultState().getValue(BlockStairs.FACING), odd.getValue(BlockStairs.FACING));
    }

    @Test
    public void accuratePlacementSetsOnlyOrientationLikeProperties() {
        IBlockState placed = Blocks.STONE_STAIRS.getDefaultState();
        IBlockState wanted = placed.withProperty(BlockStairs.FACING, EnumFacing.SOUTH).withProperty(BlockStairs.HALF, BlockStairs.EnumHalf.TOP);
        IBlockState merged = AccuratePlacement.merge(placed, wanted);
        assertNotNull(merged);
        assertEquals(EnumFacing.SOUTH, merged.getValue(BlockStairs.FACING));
        assertEquals(BlockStairs.EnumHalf.TOP, merged.getValue(BlockStairs.HALF));
        // nothing to change: no merge
        assertNull(AccuratePlacement.merge(wanted, wanted));
        // a repeater's delay is set, a powered twin's other properties are matched by name
        IBlockState repeater = Blocks.UNPOWERED_REPEATER.getDefaultState();
        IBlockState delayed = AccuratePlacement.merge(repeater, Blocks.POWERED_REPEATER.getDefaultState()
            .withProperty(BlockRedstoneRepeater.DELAY, 4).withProperty(BlockHorizontal.FACING, EnumFacing.EAST));
        assertEquals(4, (int) delayed.getValue(BlockRedstoneRepeater.DELAY));
        assertEquals(EnumFacing.EAST, delayed.getValue(BlockHorizontal.FACING));
        assertEquals(Blocks.UNPOWERED_REPEATER, delayed.getBlock());
    }

    @Test
    public void accuratePlacementSetsWhatAPlayerCouldClickButNotPower() {
        // a lever's switch is the player's
        IBlockState lever = Blocks.LEVER.getDefaultState().withProperty(BlockLever.FACING, BlockLever.EnumOrientation.EAST);
        assertTrue(AccuratePlacement.merge(lever, lever.withProperty(BlockLever.POWERED, true)).getValue(BlockLever.POWERED));
        // a wooden trapdoor opens, top half, any facing
        IBlockState trapdoor = Blocks.TRAPDOOR.getDefaultState();
        IBlockState open = trapdoor.withProperty(BlockTrapDoor.OPEN, true).withProperty(BlockTrapDoor.HALF, BlockTrapDoor.DoorHalf.TOP)
            .withProperty(BlockTrapDoor.FACING, EnumFacing.EAST);
        assertEquals(open, AccuratePlacement.merge(trapdoor, open));
        // an iron trapdoor stays closed: only redstone opens it
        IBlockState iron = Blocks.IRON_TRAPDOOR.getDefaultState();
        assertNull(AccuratePlacement.merge(iron, iron.withProperty(BlockTrapDoor.OPEN, true)));
        // redstone parts start unpowered, the world powers them
        IBlockState rail = Blocks.GOLDEN_RAIL.getDefaultState();
        assertNull(AccuratePlacement.merge(rail, rail.withProperty(BlockRailPowered.POWERED, true)));
        IBlockState gate = Blocks.OAK_FENCE_GATE.getDefaultState();
        IBlockState gateMerged = AccuratePlacement.merge(gate, gate.withProperty(BlockFenceGate.OPEN, true).withProperty(BlockFenceGate.POWERED, true));
        assertTrue(gateMerged.getValue(BlockFenceGate.OPEN));
        assertFalse(gateMerged.getValue(BlockFenceGate.POWERED));
        IBlockState piston = Blocks.PISTON.getDefaultState();
        assertNull(AccuratePlacement.merge(piston, piston.withProperty(BlockPistonBase.EXTENDED, true)));
        // the block's kind is never changed: dirt does not become podzol, white wool not red
        IBlockState dirt = Blocks.DIRT.getDefaultState();
        assertNull(AccuratePlacement.merge(dirt, dirt.withProperty(BlockDirt.VARIANT, BlockDirt.DirtType.PODZOL)));
        IBlockState wool = Blocks.WOOL.getDefaultState();
        assertNull(AccuratePlacement.merge(wool, wool.withProperty(BlockColored.COLOR, EnumDyeColor.RED)));
        // nor its growth, nor an end portal frame's eye
        IBlockState frame = Blocks.END_PORTAL_FRAME.getDefaultState();
        assertNull(AccuratePlacement.merge(frame, frame.withProperty(BlockEndPortalFrame.EYE, true)));
    }

    @Test
    public void accuratePlacementKnowsWhichBlocksItCanSet() {
        assertTrue(AccuratePlacement.supports(Blocks.STONE_STAIRS));
        assertTrue(AccuratePlacement.supports(Blocks.PISTON));
        assertTrue(AccuratePlacement.supports(Blocks.QUARTZ_BLOCK));
        assertTrue(AccuratePlacement.supports(Blocks.UNPOWERED_COMPARATOR));
        assertTrue(AccuratePlacement.supports(Blocks.LEVER));
        assertTrue(AccuratePlacement.supports(Blocks.TRAPDOOR));
        assertFalse(AccuratePlacement.supports(Blocks.WOOL));
        assertFalse(AccuratePlacement.supports(Blocks.REDSTONE_LAMP));
        assertFalse(AccuratePlacement.supports(Blocks.STONE));
        assertFalse(AccuratePlacement.supports(Blocks.DIRT));
        assertFalse(AccuratePlacement.supports(null));
    }

    @Test
    public void airPlacementIsForFenceGatesOnly() {
        assertTrue(AirPlacement.supports(Blocks.OAK_FENCE_GATE.getDefaultState()));
        assertFalse(AirPlacement.supports(Blocks.STONE.getDefaultState()));
        assertFalse(AirPlacement.supports(null));
    }

    @Test
    public void serverOptionsHaveSchematicaUpgradedsDefaults() {
        // reading and saving the file needs Forge's Loader (the in-game run checks the saved file after the admin command)
        assertTrue(ServerConfig.PRINTER_ALLOWED_DEFAULT && ServerConfig.ACCURATE_DEFAULT && ServerConfig.AIR_DEFAULT && ServerConfig.SHULKER_DEFAULT);
        assertEquals(16, ServerConfig.MAX_RANGE_DEFAULT, 0);
        assertEquals(64, ServerConfig.clampRange(1000), 0);
        assertEquals(0, ServerConfig.clampRange(-3), 0);
        assertEquals(12.5, ServerConfig.clampRange(12.5), 0);
    }

    @Test
    public void theClientHoldsWhatTheServerSaidAndForgetsOnReset() {
        ServerFeatures.reset();
        assertFalse(ServerFeatures.received);
        assertTrue("without a server part the printer is allowed", ServerFeatures.printerAllowed);
        assertEquals(6, PrinterRange.cap(), 0);
        ServerFeatures.offer(new MessageCapabilities(false, true, true, false, 16));
        assertFalse("a message waits for the client thread", ServerFeatures.received);
        assertNotNull(ServerFeatures.takePending());
        assertNull(ServerFeatures.takePending());
        assertTrue(ServerFeatures.received && !ServerFeatures.printerAllowed && ServerFeatures.accuratePlacement && ServerFeatures.airPlacement && !ServerFeatures.shulkerRestock);
        assertEquals(16, PrinterRange.cap(), 0);
        ServerFeatures.reset();
        assertFalse(ServerFeatures.received || ServerFeatures.accuratePlacement || ServerFeatures.airPlacement || ServerFeatures.shulkerRestock);
        assertTrue(ServerFeatures.printerAllowed);
        assertEquals(0, ServerFeatures.range, 0);
    }

    @Test
    public void theAdminCommandNeedsPermissionLevelTwo() {
        PrinterCommand command = new PrinterCommand();
        assertEquals(2, command.getRequiredPermissionLevel());
        assertEquals("litematicaupgraded", command.getName());
        assertEquals("litematica_upgraded.command.usage", command.getUsage(null));
    }

    @Test
    public void shulkerContentsAreReadAndWrittenLikeVanillaStoresThem() {
        ItemStack box = new ItemStack(Blocks.WHITE_SHULKER_BOX);
        assertTrue(ShulkerRestock.isBox(box));
        assertFalse(ShulkerRestock.isBox(new ItemStack(Blocks.STONE)));
        assertTrue(ShulkerRestock.contents(box).stream().allMatch(ItemStack::isEmpty));
        NonNullList<ItemStack> items = NonNullList.withSize(ShulkerRestock.BOX_SLOTS, ItemStack.EMPTY);
        items.set(4, new ItemStack(Blocks.COBBLESTONE, 12));
        ShulkerRestock.store(box, items);
        NonNullList<ItemStack> back = ShulkerRestock.contents(box);
        assertEquals(12, back.get(4).getCount());
        assertEquals(net.minecraft.item.Item.getItemFromBlock(Blocks.COBBLESTONE), back.get(4).getItem());
        // an emptied box carries no tag at all
        ShulkerRestock.store(box, NonNullList.withSize(ShulkerRestock.BOX_SLOTS, ItemStack.EMPTY));
        assertNull(box.getTagCompound());
    }
}
