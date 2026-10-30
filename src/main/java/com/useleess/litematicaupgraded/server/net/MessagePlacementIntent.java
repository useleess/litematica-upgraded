// Accurate placement intent, after Schematica Upgraded (Lunatrius / HackerRouter / Useleess, MIT), itself the counterpart of Litematica's easyPlaceProtocol v3.
package com.useleess.litematicaupgraded.server.net;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.useleess.litematicaupgraded.server.AccuratePlacement;

/** Client to server, just before a placement click: the block state (name[properties]) the next placement at x, y, z should end with. */
public final class MessagePlacementIntent implements IMessage, IMessageHandler<MessagePlacementIntent, IMessage> {
    public static final int MAX_STATE_BYTES = 1024;
    public int x, y, z;
    public String state = "";

    public MessagePlacementIntent() {}

    public MessagePlacementIntent(int x, int y, int z, String state) {
        this.x = x; this.y = y; this.z = z; this.state = state;
    }

    @Override public void fromBytes(ByteBuf buffer) {
        x = buffer.readInt(); y = buffer.readInt(); z = buffer.readInt();
        state = readState(buffer);
    }

    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeInt(x); buffer.writeInt(y); buffer.writeInt(z);
        writeState(buffer, state);
    }

    /** A length-prefixed state string that must fill the rest of the buffer. */
    static String readState(ByteBuf buffer) {
        int length = buffer.readUnsignedShort();
        if (length > MAX_STATE_BYTES || length != buffer.readableBytes()) throw new IllegalArgumentException("Invalid state string");
        byte[] name = new byte[length];
        buffer.readBytes(name);
        return new String(name, StandardCharsets.UTF_8);
    }

    static void writeState(ByteBuf buffer, String state) {
        byte[] name = state.getBytes(StandardCharsets.UTF_8);
        buffer.writeShort(name.length);
        buffer.writeBytes(name);
    }

    @Override public IMessage onMessage(MessagePlacementIntent message, MessageContext context) {
        AccuratePlacement.INSTANCE.intent(context.getServerHandler().player, message);
        return null;
    }
}
