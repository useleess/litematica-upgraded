package com.useleess.litematicaupgraded.server.net;

import io.netty.buffer.ByteBuf;

/** The byte payload of a slice message (save data, edit upload): length, then the bytes. */
final class SliceBytes {
    private SliceBytes() {}

    static byte[] read(ByteBuf buffer) {
        int length = buffer.readInt();
        if (length < 0 || length > buffer.readableBytes()) throw new IllegalArgumentException("bad slice length");
        byte[] data = new byte[length];
        buffer.readBytes(data);
        return data;
    }

    static void write(ByteBuf buffer, byte[] data) {
        buffer.writeInt(data.length);
        buffer.writeBytes(data);
    }
}
