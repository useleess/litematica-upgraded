// One slice of an edit (paste or fill) a client uploads to the server, after Schematica Upgraded's MessageEditUpload (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.server.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.useleess.litematicaupgraded.server.ServerJobs;

/** Client to server: slice index of count of the edit id, for the dimension the player is in. */
public final class MessageEditUpload implements IMessage, IMessageHandler<MessageEditUpload, IMessage> {
    public long id;
    public int dimension, index, count;
    public byte[] data = new byte[0];

    public MessageEditUpload() {}

    public MessageEditUpload(long id, int dimension, int index, int count, byte[] data) {
        this.id = id; this.dimension = dimension; this.index = index; this.count = count; this.data = data;
    }

    @Override public void fromBytes(ByteBuf buffer) {
        id = buffer.readLong();
        dimension = buffer.readInt();
        index = buffer.readInt();
        count = buffer.readInt();
        data = SliceBytes.read(buffer);
    }

    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeLong(id);
        buffer.writeInt(dimension);
        buffer.writeInt(index);
        buffer.writeInt(count);
        SliceBytes.write(buffer, data);
    }

    @Override public IMessage onMessage(MessageEditUpload message, MessageContext context) {
        EntityPlayerMP player = context.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> ServerJobs.INSTANCE.upload(player, message));
        return null;
    }
}
