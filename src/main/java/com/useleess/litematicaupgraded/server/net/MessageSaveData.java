// One slice of a saved area, server to client, after Schematica Upgraded's MessageDownloadChunk (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.server.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.useleess.litematicaupgraded.server.RemoteClient;

/** Server to client: slice index of count of the save id. */
public final class MessageSaveData implements IMessage, IMessageHandler<MessageSaveData, IMessage> {
    public long id;
    public int index, count;
    public byte[] data = new byte[0];

    public MessageSaveData() {}

    public MessageSaveData(long id, int index, int count, byte[] data) {
        this.id = id; this.index = index; this.count = count; this.data = data;
    }

    @Override public void fromBytes(ByteBuf buffer) {
        id = buffer.readLong();
        index = buffer.readInt();
        count = buffer.readInt();
        data = SliceBytes.read(buffer);
    }

    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeLong(id);
        buffer.writeInt(index);
        buffer.writeInt(count);
        SliceBytes.write(buffer, data);
    }

    /** Network thread: only stores the slice; the client task reads it on the game thread. */
    @Override public IMessage onMessage(MessageSaveData message, MessageContext context) {
        RemoteClient.onData(message);
        return null;
    }
}
