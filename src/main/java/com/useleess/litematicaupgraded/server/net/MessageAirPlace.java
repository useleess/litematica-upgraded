// Printer air placement request, after Schematica Upgraded (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.server.net;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.useleess.litematicaupgraded.server.AirPlacement;

/** Client to server: place the held block at x, y, z in the air as the given state (name[properties]). */
public final class MessageAirPlace implements IMessage, IMessageHandler<MessageAirPlace, IMessage> {
    public int x, y, z;
    public String state = "";

    public MessageAirPlace() {}

    public MessageAirPlace(BlockPos pos, String state) {
        this.x = pos.getX(); this.y = pos.getY(); this.z = pos.getZ(); this.state = state;
    }

    @Override public void fromBytes(ByteBuf buffer) {
        x = buffer.readInt(); y = buffer.readInt(); z = buffer.readInt();
        state = MessagePlacementIntent.readState(buffer);
    }

    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeInt(x); buffer.writeInt(y); buffer.writeInt(z);
        MessagePlacementIntent.writeState(buffer, state);
    }

    @Override public IMessage onMessage(MessageAirPlace message, MessageContext context) {
        EntityPlayerMP player = context.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> AirPlacement.place(player, new BlockPos(message.x, message.y, message.z), message.state));
        return null;
    }
}
