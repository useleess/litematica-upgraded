// What the server part supports, sent to a client at join and again on each change. After Schematica Upgraded's MessageCapabilities (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.server.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.useleess.litematicaupgraded.server.ServerFeatures;

/** Server to client. Fields missing at the end of an older / shorter message read as off. */
public final class MessageCapabilities implements IMessage, IMessageHandler<MessageCapabilities, IMessage> {
    public boolean printerAllowed, accuratePlacement, airPlacement, shulkerRestock;
    public float range;
    /** Server-side saving and edits are on; the largest edit upload in KiB. */
    public boolean saving, edits;
    public int uploadKiB;
    /** Accurate placement sets every state a player could click into the block (open, a lever's switch...), not only its orientation. */
    public boolean accurateStates;

    public MessageCapabilities() {}

    public MessageCapabilities(boolean printerAllowed, boolean accuratePlacement, boolean airPlacement, boolean shulkerRestock, float range) {
        this.printerAllowed = printerAllowed;
        this.accuratePlacement = accuratePlacement;
        this.airPlacement = airPlacement;
        this.shulkerRestock = shulkerRestock;
        this.range = range;
    }

    public MessageCapabilities(boolean printerAllowed, boolean accuratePlacement, boolean airPlacement, boolean shulkerRestock, float range,
                               boolean saving, boolean edits, int uploadKiB) {
        this(printerAllowed, accuratePlacement, airPlacement, shulkerRestock, range);
        this.saving = saving;
        this.edits = edits;
        this.uploadKiB = uploadKiB;
    }

    @Override public void fromBytes(ByteBuf buffer) {
        printerAllowed = buffer.isReadable() && buffer.readBoolean();
        accuratePlacement = buffer.isReadable() && buffer.readBoolean();
        airPlacement = buffer.isReadable() && buffer.readBoolean();
        shulkerRestock = buffer.isReadable() && buffer.readBoolean();
        range = buffer.readableBytes() >= 4 ? buffer.readFloat() : 0;
        saving = buffer.isReadable() && buffer.readBoolean();
        edits = buffer.isReadable() && buffer.readBoolean();
        uploadKiB = buffer.readableBytes() >= 4 ? buffer.readInt() : 0;
        accurateStates = buffer.isReadable() && buffer.readBoolean();
    }

    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeBoolean(printerAllowed);
        buffer.writeBoolean(accuratePlacement);
        buffer.writeBoolean(airPlacement);
        buffer.writeBoolean(shulkerRestock);
        buffer.writeFloat(range);
        buffer.writeBoolean(saving);
        buffer.writeBoolean(edits);
        buffer.writeInt(uploadKiB);
        buffer.writeBoolean(accurateStates);
    }

    /** Runs on the network thread; the client applies it on its own thread (ServerFeatures.takePending). */
    @Override public IMessage onMessage(MessageCapabilities message, MessageContext context) {
        ServerFeatures.offer(message);
        return null;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof MessageCapabilities)) return false;
        MessageCapabilities o = (MessageCapabilities) other;
        return printerAllowed == o.printerAllowed && accuratePlacement == o.accuratePlacement && airPlacement == o.airPlacement
            && shulkerRestock == o.shulkerRestock && range == o.range
            && saving == o.saving && edits == o.edits && uploadKiB == o.uploadKiB && accurateStates == o.accurateStates;
    }

    @Override public int hashCode() {
        return (printerAllowed ? 1 : 0) | (accuratePlacement ? 2 : 0) | (airPlacement ? 4 : 0) | (shulkerRestock ? 8 : 0) | (saving ? 16 : 0) | (edits ? 32 : 0) | (accurateStates ? 64 : 0) | Float.floatToIntBits(range) << 6 ^ uploadKiB;
    }

    @Override public String toString() {
        return "capabilities{printer=" + printerAllowed + ", accurate=" + accuratePlacement + ", air=" + airPlacement
            + ", shulker=" + shulkerRestock + ", range=" + range
            + ", saving=" + saving + ", edits=" + edits + ", uploadKiB=" + uploadKiB + ", accurateStates=" + accurateStates + "}";
    }
}
