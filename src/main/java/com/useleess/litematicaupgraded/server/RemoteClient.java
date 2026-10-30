// What the client knows about its server-side saves and edits: the messages of the server land here, the tasks read them on the game thread.
package com.useleess.litematicaupgraded.server;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.nbt.NBTTagCompound;

import com.useleess.litematicaupgraded.server.net.MessageEditCancel;
import com.useleess.litematicaupgraded.server.net.MessageEditStatus;
import com.useleess.litematicaupgraded.server.net.MessageEditUpload;
import com.useleess.litematicaupgraded.server.net.MessageSaveData;
import com.useleess.litematicaupgraded.server.net.Net;

/** Holds plain fields only (no client classes), so the network handlers can reach it from any thread. */
public final class RemoteClient {
    public static final int SENT = -1;
    private static final long MAX_DOWNLOAD_BYTES = 512L * 1024 * 1024;
    private static final AtomicLong IDS = new AtomicLong(System.nanoTime() & 0xFFFFFFFFFFL);
    private static final Map<Long, Remote> REMOTES = new ConcurrentHashMap<>();

    /** How many server-side jobs this connection started, and how many statuses of each kind arrived (for tests and the debug output). */
    public static volatile int started;
    public static volatile String lastRefusal = "";

    private RemoteClient() {}

    /** One save or edit the server works on. */
    public static final class Remote {
        public final long id;
        public volatile int state = SENT;
        public volatile String key = "";
        public volatile int completed, total;
        public volatile long affected;
        /** How many progress messages arrived, and the highest progress seen before the end. */
        public volatile int progressMessages;
        public volatile Slices.Assembler slices;

        Remote(long id) {
            this.id = id;
        }

        public boolean ended() {
            return state == MessageEditStatus.FINISHED || state == MessageEditStatus.FAILED || state == MessageEditStatus.REFUSED
                || state == MessageEditStatus.CANCELLED;
        }

        /** The server's answer about the download: the slices of a finished save are all here. */
        public boolean downloaded() {
            Slices.Assembler assembler = slices;
            return assembler != null && assembler.complete();
        }

        /** The data of a finished save. */
        public synchronized NBTTagCompound result() throws java.io.IOException {
            return RemoteBlob.read(slices.join());
        }
    }

    public static Remote begin() {
        Remote remote = new Remote(IDS.incrementAndGet());
        REMOTES.put(remote.id, remote);
        started++;
        return remote;
    }

    public static void forget(Remote remote) {
        REMOTES.remove(remote.id);
    }

    /** A new connection or none: nothing of an earlier one is kept. */
    public static void reset() {
        REMOTES.clear();
        lastRefusal = "";
    }

    public static void onStatus(MessageEditStatus message) {
        Remote remote = REMOTES.get(message.id);
        if (remote == null) return;
        remote.completed = message.completed;
        remote.total = message.total;
        remote.affected = message.affected;
        remote.key = message.key;
        if (message.state == MessageEditStatus.PROGRESS) remote.progressMessages++;
        if (message.state == MessageEditStatus.REFUSED) lastRefusal = message.key;
        // a progress message never undoes an end that was already seen
        if (!remote.ended()) remote.state = message.state;
        if (remote.ended()) REMOTES.remove(remote.id);
    }

    public static void onData(MessageSaveData message) {
        Remote remote = REMOTES.get(message.id);
        if (remote == null) return;
        synchronized (remote) {
            if (remote.slices == null) {
                if (message.count < 1 || Slices.exceedsCap(message.count, Slices.SLICE_BYTES, MAX_DOWNLOAD_BYTES)) return;
                remote.slices = new Slices.Assembler(message.count, MAX_DOWNLOAD_BYTES);
            }
            remote.slices.put(message.index, message.data);
        }
    }

    /** Sends an edit (gzip NBT) in slices. */
    public static void upload(Remote remote, int dimension, byte[] data) {
        List<byte[]> slices = Slices.split(data, Slices.SLICE_BYTES);
        for (int i = 0; i < slices.size(); i++) Net.CHANNEL.sendToServer(new MessageEditUpload(remote.id, dimension, i, slices.size(), slices.get(i)));
    }

    public static void cancel(Remote remote) {
        Net.CHANNEL.sendToServer(new MessageEditCancel(remote.id));
    }
}
