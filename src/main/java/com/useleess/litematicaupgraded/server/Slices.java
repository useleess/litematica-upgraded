// Slicing and reassembly of the big transfers (uploads of edits, downloads of saved areas), after Schematica Upgraded's SchematicTransfer (Lunatrius / HackerRouter / Useleess, MIT).
package com.useleess.litematicaupgraded.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pure logic, no game classes: a byte array is cut into slices that fit one packet and put together again. */
public final class Slices {
    /** One slice's size; a client packet may hold at most 32767 bytes. */
    public static final int SLICE_BYTES = 30000;

    private Slices() {}

    public static int count(int length, int size) {
        return Math.max(1, (length + size - 1) / size);
    }

    public static List<byte[]> split(byte[] data, int size) {
        List<byte[]> slices = new ArrayList<>();
        for (int from = 0; from < data.length || slices.isEmpty(); from += size) {
            slices.add(Arrays.copyOfRange(data, from, Math.min(data.length, from + size)));
        }
        return slices;
    }

    /** Whether a transfer announced as count slices can hold more than cap bytes. */
    public static boolean exceedsCap(int count, int size, long cap) {
        return count < 1 || (long) count * size > cap + size;
    }

    /** Collects the slices of one transfer in any order. */
    public static final class Assembler {
        public static final String INVALID = "invalid", TOO_LARGE = "too_large";
        private final byte[][] parts;
        private final long cap;
        private int received;
        private long bytes;

        public Assembler(int count, long cap) {
            this.parts = new byte[count][];
            this.cap = cap;
        }

        /** Stores a slice; null when it was accepted, else the reason ({@link #INVALID} or {@link #TOO_LARGE}). */
        public String put(int index, byte[] data) {
            if (index < 0 || index >= parts.length || parts[index] != null || data == null) return INVALID;
            parts[index] = data;
            received++;
            bytes += data.length;
            return bytes > cap ? TOO_LARGE : null;
        }

        public int received() { return received; }

        public int count() { return parts.length; }

        public boolean complete() { return received == parts.length; }

        public byte[] join() {
            byte[] all = new byte[(int) bytes];
            int at = 0;
            for (byte[] part : parts) {
                System.arraycopy(part, 0, all, at, part.length);
                at += part.length;
            }
            return all;
        }
    }
}
