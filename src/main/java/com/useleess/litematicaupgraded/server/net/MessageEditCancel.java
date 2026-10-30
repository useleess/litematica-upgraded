// A client cancels its running server-side edit or save, after Schematica Upgraded's MessageEditCancel (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.server.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.useleess.litematicaupgraded.server.ServerJobs;

/** Client to server: stop the job with this id (what was changed so far stays). */
public final class MessageEditCancel implements IMessage, IMessageHandler<MessageEditCancel, IMessage> {
    public long id;

    public MessageEditCancel() {}

    public MessageEditCancel(long id) {
        this.id = id;
    }

    @Override public void fromBytes(ByteBuf buffer) { id = buffer.readLong(); }

    @Override public void toBytes(ByteBuf buffer) { buffer.writeLong(id); }

    @Override public IMessage onMessage(MessageEditCancel message, MessageContext context) {
        EntityPlayerMP player = context.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> ServerJobs.INSTANCE.cancel(player, message.id));
        return null;
    }
}
