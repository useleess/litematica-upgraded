package com.useleess.litematicaupgraded.perf;

import java.util.Arrays;

/**
 * A set of int indices kept in lazily allocated pages of 65536 bits, so a mask over a huge, mostly empty box only takes
 * memory where something is set (from Schematica Upgraded, MIT: Lunatrius / HackerRouter / Useleess).
 */
public final class SparseBits {
    private static final int PAGE_BITS = 16;
    private static final int PAGE_WORDS = 1 << (PAGE_BITS - 6);
    private static final int PAGE_MASK = (1 << PAGE_BITS) - 1;

    private long[][] pages = new long[0][];

    public boolean get(int index) {
        if (index < 0) return false;
        int page = index >>> PAGE_BITS;
        if (page >= pages.length) return false;
        long[] words = pages[page];
        return words != null && (words[(index & PAGE_MASK) >>> 6] & 1L << index) != 0;
    }

    public void set(int index) {
        if (index < 0) throw new IndexOutOfBoundsException("index < 0: " + index);
        long[] words = writable(index >>> PAGE_BITS);
        words[(index & PAGE_MASK) >>> 6] |= 1L << index;
    }

    public void clear(int index) {
        if (index < 0 || (index >>> PAGE_BITS) >= pages.length || pages[index >>> PAGE_BITS] == null) return;
        pages[index >>> PAGE_BITS][(index & PAGE_MASK) >>> 6] &= ~(1L << index);
    }

    /** The first set index at or after {@code from}, or -1. */
    public int nextSetBit(int from) {
        if (from < 0) from = 0;
        for (int page = from >>> PAGE_BITS; page < pages.length; page++) {
            long[] words = pages[page];
            if (words == null) continue;
            int start = page == from >>> PAGE_BITS ? from & PAGE_MASK : 0;
            for (int word = start >>> 6; word < PAGE_WORDS; word++) {
                long bits = words[word];
                if (word == start >>> 6) bits &= -1L << start;
                if (bits != 0) return (page << PAGE_BITS) | (word << 6) | Long.numberOfTrailingZeros(bits);
            }
        }
        return -1;
    }

    /** How many pages hold memory (for tests and the memory report). */
    public int allocatedPages() {
        int n = 0;
        for (long[] page : pages) if (page != null) n++;
        return n;
    }

    private long[] writable(int page) {
        if (page >= pages.length) pages = Arrays.copyOf(pages, Math.max(page + 1, Math.min(pages.length * 2, (Integer.MAX_VALUE >>> PAGE_BITS) + 1)));
        long[] words = pages[page];
        if (words == null) pages[page] = words = new long[PAGE_WORDS];
        return words;
    }
}
