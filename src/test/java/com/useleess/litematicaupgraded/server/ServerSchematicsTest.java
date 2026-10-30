package com.useleess.litematicaupgraded.server;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.BeforeClass;
import org.junit.Test;

import com.useleess.litematicaupgraded.server.net.MessageCapabilities;
import com.useleess.litematicaupgraded.server.net.MessageEditStatus;
import com.useleess.litematicaupgraded.server.net.MessageEditUpload;
import com.useleess.litematicaupgraded.server.net.MessageSaveData;
import com.useleess.litematicaupgraded.server.net.MessageSaveRequest;

/** The pure logic of the server-side schematics: slicing and reassembly, the size cap, the region data, the messages. */
public class ServerSchematicsTest {
    @BeforeClass
    public static void bootstrap() {
        Bootstrap.register();
    }

    private static ByteBuf encode(net.minecraftforge.fml.common.network.simpleimpl.IMessage message) {
        ByteBuf buffer = Unpooled.buffer();
        message.toBytes(buffer);
        return buffer;
    }

    private static byte[] bytes(int length) {
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) data[i] = (byte) (i * 31 + 7);
        return data;
    }

    @Test
    public void slicesCutAndJoinBack() {
        for (int length : new int[] {0, 1, 29999, 30000, 30001, 95000}) {
            byte[] data = bytes(length);
            List<byte[]> slices = Slices.split(data, Slices.SLICE_BYTES);
            assertEquals(Slices.count(length, Slices.SLICE_BYTES), slices.size());
            for (byte[] slice : slices) assertTrue(slice.length <= Slices.SLICE_BYTES);
            Slices.Assembler assembler = new Slices.Assembler(slices.size(), 1 << 20);
            // in reverse order: the order of arrival does not matter
            for (int i = slices.size() - 1; i >= 0; i--) {
                assertFalse(assembler.complete());
                assertNull(assembler.put(i, slices.get(i)));
            }
            assertTrue(assembler.complete());
            assertArrayEquals(data, assembler.join());
        }
    }

    @Test
    public void assemblerRefusesBadSlices() {
        Slices.Assembler assembler = new Slices.Assembler(3, 100);
        assertEquals(Slices.Assembler.INVALID, assembler.put(3, new byte[1]));
        assertEquals(Slices.Assembler.INVALID, assembler.put(-1, new byte[1]));
        assertNull(assembler.put(0, new byte[60]));
        assertEquals("a slice twice", Slices.Assembler.INVALID, assembler.put(0, new byte[1]));
        assertEquals("over the cap", Slices.Assembler.TOO_LARGE, assembler.put(1, new byte[60]));
    }

    @Test
    public void announcedSizeIsCheckedAgainstTheCap() {
        long cap = 32L * 1024 * 1024;
        assertFalse(Slices.exceedsCap(1, Slices.SLICE_BYTES, cap));
        assertFalse(Slices.exceedsCap(Slices.count((int) cap, Slices.SLICE_BYTES), Slices.SLICE_BYTES, cap));
        assertTrue(Slices.exceedsCap(2000, Slices.SLICE_BYTES, cap));
        assertTrue("an upload with no slices", Slices.exceedsCap(0, Slices.SLICE_BYTES, cap));
        assertTrue(Slices.exceedsCap(5, Slices.SLICE_BYTES, 1024));
        assertFalse(Slices.exceedsCap(1, Slices.SLICE_BYTES, 1024));
    }

    @Test
    public void regionSurvivesGzipNbt() throws IOException {
        RemoteBlob.Region region = new RemoteBlob.Region("lu", 10, 64, -5, 3, 2, 2);
        region.set(10, 64, -5, StateStrings.toString(Blocks.STONE.getDefaultState()));
        region.set(12, 65, -4, StateStrings.toString(Blocks.CHEST.getDefaultState()));
        region.set(11, 64, -4, StateStrings.toString(Blocks.STONE.getDefaultState()));
        NBTTagCompound tile = new NBTTagCompound();
        tile.setInteger("x", 12);
        tile.setInteger("y", 65);
        tile.setInteger("z", -4);
        tile.setString("id", "minecraft:chest");
        region.tiles.add(tile);
        NBTTagCompound entity = new NBTTagCompound();
        entity.setString("id", "minecraft:armor_stand");
        region.entities.add(entity);

        NBTTagCompound blob = new NBTTagCompound();
        blob.setString("op", RemoteBlob.OP_PASTE);
        List<RemoteBlob.Region> list = new ArrayList<>();
        list.add(region);
        blob.setTag("regions", RemoteBlob.regionList(list));
        List<RemoteBlob.Region> back = RemoteBlob.regions(RemoteBlob.read(RemoteBlob.gzip(blob)));
        assertEquals(1, back.size());
        RemoteBlob.Region read = back.get(0);
        assertEquals("lu", read.name);
        assertEquals(10, read.x);
        assertEquals(-5, read.z);
        assertEquals(12, read.volume());
        assertArrayEquals(region.indices, read.indices);
        assertEquals(region.palette, read.palette);
        assertEquals(2, read.palette.size());
        assertEquals(-1, read.indices[read.offset(11, 65, -5)]);
        assertEquals(StateStrings.toString(Blocks.CHEST.getDefaultState()), read.palette.get(read.indices[read.offset(12, 65, -4)]));
        assertEquals(1, read.tiles.size());
        assertEquals("minecraft:chest", read.tiles.get(0).getString("id"));
        assertEquals(1, read.entities.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void regionWithAWrongIndexIsRejected() {
        RemoteBlob.Region region = new RemoteBlob.Region("lu", 0, 0, 0, 1, 1, 1);
        NBTTagCompound tag = region.toNbt();
        tag.setIntArray("indices", new int[] {4});
        RemoteBlob.Region.fromNbt(tag);
    }

    @Test(expected = IllegalArgumentException.class)
    public void regionWithTheWrongSizeIsRejected() {
        RemoteBlob.Region region = new RemoteBlob.Region("lu", 0, 0, 0, 2, 1, 1);
        NBTTagCompound tag = region.toNbt();
        tag.setIntArray("indices", new int[] {-1});
        RemoteBlob.Region.fromNbt(tag);
    }

    @Test
    public void capabilitiesCarryTheNewOptions() {
        MessageCapabilities sent = new MessageCapabilities(true, true, false, true, 16f, true, false, 32768);
        MessageCapabilities read = new MessageCapabilities();
        read.fromBytes(encode(sent));
        assertEquals(sent, read);
        assertTrue(read.saving);
        assertFalse(read.edits);
        assertEquals(32768, read.uploadKiB);
        // a message from the earlier version (no new fields) reads them as off
        ByteBuf old = Unpooled.buffer();
        old.writeBoolean(true);
        old.writeBoolean(true);
        old.writeBoolean(true);
        old.writeBoolean(true);
        old.writeFloat(16f);
        MessageCapabilities older = new MessageCapabilities();
        older.fromBytes(old);
        assertFalse(older.saving);
        assertFalse(older.edits);
        assertEquals(0, older.uploadKiB);
    }

    @Test
    public void uploadAndDataMessagesSurviveTheWire() {
        byte[] data = bytes(1234);
        MessageEditUpload upload = new MessageEditUpload(77L, -1, 3, 9, data);
        MessageEditUpload readUpload = new MessageEditUpload();
        readUpload.fromBytes(encode(upload));
        assertEquals(77L, readUpload.id);
        assertEquals(-1, readUpload.dimension);
        assertEquals(3, readUpload.index);
        assertEquals(9, readUpload.count);
        assertArrayEquals(data, readUpload.data);

        MessageSaveData part = new MessageSaveData(5L, 1, 2, data);
        MessageSaveData readPart = new MessageSaveData();
        readPart.fromBytes(encode(part));
        assertEquals(5L, readPart.id);
        assertEquals(2, readPart.count);
        assertArrayEquals(data, readPart.data);
    }

    @Test(expected = IllegalArgumentException.class)
    public void uploadWithALengthBeyondTheMessageIsRejected() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeLong(1L);
        buffer.writeInt(0);
        buffer.writeInt(0);
        buffer.writeInt(1);
        buffer.writeInt(5000);
        buffer.writeBytes(new byte[10]);
        new MessageEditUpload().fromBytes(buffer);
    }

    @Test
    public void saveRequestAndStatusSurviveTheWire() {
        List<MessageSaveRequest.Box> boxes = new ArrayList<>();
        boxes.add(new MessageSaveRequest.Box("a", 1, 2, 3, 4, 5, 6));
        boxes.add(new MessageSaveRequest.Box("b", -7, 0, 9, -1, 10, 11));
        MessageSaveRequest read = new MessageSaveRequest();
        read.fromBytes(encode(new MessageSaveRequest(42L, 0, 7, boxes)));
        assertEquals(42L, read.id);
        assertEquals(7, read.flags);
        assertEquals(2, read.boxes.size());
        assertEquals("b", read.boxes.get(1).name);
        assertArrayEquals(new int[] {-7, 0, 9, -1, 10, 11}, read.boxes.get(1).corners);

        MessageEditStatus status = MessageEditStatus.refused(9L, ServerJobs.BUSY);
        MessageEditStatus readStatus = new MessageEditStatus();
        readStatus.fromBytes(encode(status));
        assertEquals(MessageEditStatus.REFUSED, readStatus.state);
        assertEquals(ServerJobs.BUSY, readStatus.key);
        MessageEditStatus progress = new MessageEditStatus(9L, MessageEditStatus.PROGRESS);
        progress.completed = 500;
        progress.total = 2048;
        progress.affected = 123456789012L;
        MessageEditStatus readProgress = new MessageEditStatus();
        readProgress.fromBytes(encode(progress));
        assertEquals(500, readProgress.completed);
        assertEquals(2048, readProgress.total);
        assertEquals(123456789012L, readProgress.affected);
    }

    @Test(expected = IllegalArgumentException.class)
    public void saveRequestWithTooManyBoxesIsRejected() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeLong(1L);
        buffer.writeInt(0);
        buffer.writeByte(7);
        buffer.writeInt(MessageSaveRequest.MAX_BOXES + 1);
        new MessageSaveRequest().fromBytes(buffer);
    }

    @Test
    public void clientStateFollowsTheServersStatus() {
        RemoteClient.Remote remote = RemoteClient.begin();
        MessageEditStatus progress = new MessageEditStatus(remote.id, MessageEditStatus.PROGRESS);
        progress.completed = 10;
        progress.total = 100;
        RemoteClient.onStatus(progress);
        assertEquals(MessageEditStatus.PROGRESS, remote.state);
        assertEquals(1, remote.progressMessages);
        RemoteClient.onStatus(new MessageEditStatus(remote.id, MessageEditStatus.FINISHED));
        assertTrue(remote.ended());
        // a late progress message does not undo the end
        RemoteClient.onStatus(progress);
        assertEquals(MessageEditStatus.FINISHED, remote.state);
        RemoteClient.reset();
    }

    @Test
    public void downloadedSliceAreCollectedByTheClient() throws IOException {
        NBTTagCompound blob = new NBTTagCompound();
        blob.setTag("regions", new NBTTagList());
        List<byte[]> slices = Slices.split(RemoteBlob.gzip(blob), 20);
        RemoteClient.Remote remote = RemoteClient.begin();
        for (int i = slices.size() - 1; i >= 0; i--) {
            assertFalse(remote.downloaded());
            RemoteClient.onData(new MessageSaveData(remote.id, i, slices.size(), slices.get(i)));
        }
        assertTrue(remote.downloaded());
        assertEquals(0, RemoteBlob.regions(remote.result()).size());
        RemoteClient.reset();
    }

    @Test
    public void serverOptionsAreClamped() {
        assertEquals(0, ServerConfig.clampLevel(-3));
        assertEquals(4, ServerConfig.clampLevel(9));
        assertEquals(1, ServerConfig.clampUpload(0));
        assertEquals(262144, ServerConfig.clampUpload(Integer.MAX_VALUE));
        assertEquals(32L * 1024 * 1024, (long) ServerConfig.UPLOAD_KIB_DEFAULT * 1024);
    }
}
