// A client asks the server to read an area (save), after Schematica Upgraded's MessageDownloadBegin (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.server.net;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import com.useleess.litematicaupgraded.server.ServerJobs;

/** Client to server: read these boxes (name and two corners each) and send them back in slices. */
public final class MessageSaveRequest implements IMessage, IMessageHandler<MessageSaveRequest, IMessage> {
    public static final int BLOCKS = 1, TILES = 2, ENTITIES = 4;
    public static final int MAX_BOXES = 4096;

    /** A named box: x1 y1 z1 x2 y2 z2 in the order the player picked them. */
    public static final class Box {
        public final String name;
        public final int[] corners;

        public Box(String name, int... corners) {
            this.name = name;
            this.corners = corners;
        }
    }

    public long id;
    public int dimension, flags;
    public List<Box> boxes = new ArrayList<>();

    public MessageSaveRequest() {}

    public MessageSaveRequest(long id, int dimension, int flags, List<Box> boxes) {
        this.id = id; this.dimension = dimension; this.flags = flags; this.boxes = boxes;
    }

    @Override public void fromBytes(ByteBuf buffer) {
        id = buffer.readLong();
        dimension = buffer.readInt();
        flags = buffer.readUnsignedByte();
        int count = buffer.readInt();
        if (count < 0 || count > MAX_BOXES) throw new IllegalArgumentException("bad box count");
        for (int i = 0; i < count; i++) {
            String name = ByteBufUtils.readUTF8String(buffer);
            int[] corners = new int[6];
            for (int k = 0; k < 6; k++) corners[k] = buffer.readInt();
            boxes.add(new Box(name, corners));
        }
    }

    @Override public void toBytes(ByteBuf buffer) {
        buffer.writeLong(id);
        buffer.writeInt(dimension);
        buffer.writeByte(flags);
        buffer.writeInt(boxes.size());
        for (Box box : boxes) {
            ByteBufUtils.writeUTF8String(buffer, box.name);
            for (int corner : box.corners) buffer.writeInt(corner);
        }
    }

    @Override public IMessage onMessage(MessageSaveRequest message, MessageContext context) {
        EntityPlayerMP player = context.getServerHandler().player;
        player.getServerWorld().addScheduledTask(() -> ServerJobs.INSTANCE.save(player, message));
        return null;
    }
}
