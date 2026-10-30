// What the server tells a client about its save / edit, after Schematica Upgraded's MessageEditStatus (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.server.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.useleess.litematicaupgraded.server.RemoteClient;

/** Server to client. A refusal carries a translation key in {@link #key}; progress counts blocks done of total. */
public final class MessageEditStatus implements IMessage, IMessageHandler<MessageEditStatus, IMessage> {
    public static final int ACCEPTED = 0, PROGRESS = 1, FINISHED = 2, FAILED = 3, REFUSED = 4, CANCELLED = 5;

    public long id;
    public int state, completed, total;
    public long affected;
    public String key = "";

    public MessageEditStatus() {}

    public MessageEditStatus(long id, int state) {
        this.id = id; this.state = state;
    }

    public static MessageEditStatus refused(long id, String key) {
        MessageEditStatus status = new MessageEditStatus(id, REFUSED);
        status.key = key;
        return status;
    }

    @Override public void fromBytes(ByteBuf buffer) {
        id = buffer.readLong();
        state = buffer.readUnsignedByte();
        completed = buffer.readInt();
        total = buffer.readInt();
        affected = buffer.readLong();
        key = ByteBufUtils.readUTF8String(buffer);
    }

    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeLong(id);
        buffer.writeByte(state);
        buffer.writeInt(completed);
        buffer.writeInt(total);
        buffer.writeLong(affected);
        ByteBufUtils.writeUTF8String(buffer, key);
    }

    /** Network thread: only stores the status; the client task reads it on the game thread. */
    @Override public IMessage onMessage(MessageEditStatus message, MessageContext context) {
        RemoteClient.onStatus(message);
        return null;
    }
}
