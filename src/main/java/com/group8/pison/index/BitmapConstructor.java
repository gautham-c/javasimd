package com.group8.pison.index;

import com.group8.pison.partition.DynamicPartitioner;
import com.group8.pison.util.UnsafeMemory;
import com.group8.pison.util.VectorUtils;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;

import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;

/**
 * Main driver: five‐stage, parallel structural index construction.
 */
public class BitmapConstructor {
    // Off‐heap pointers for our metacharacter bitmaps
    private final long colonAddr, commaAddr, quoteAddr, slashAddr, ldelimAddr, rdelimAddr;
    private final int words;     // # 64‐bit words = ceil(json.length/64)
    private final byte[] json;
    private final int threads;

    public BitmapConstructor(byte[] json, int threads) {
        this.json    = json;
        this.threads = threads;
        this.words   = (json.length + 63) / 64;
        long bytes   = (long) words * Long.BYTES;

        // allocate off‐heap buffers
        colonAddr  = UnsafeMemory.allocate(bytes);
        commaAddr  = UnsafeMemory.allocate(bytes);
        quoteAddr  = UnsafeMemory.allocate(bytes);
        slashAddr  = UnsafeMemory.allocate(bytes);
        ldelimAddr = UnsafeMemory.allocate(bytes);
        rdelimAddr = UnsafeMemory.allocate(bytes);
    }

    /** Free all off‐heap buffers. */
    public void cleanup() {
        UnsafeMemory.free(colonAddr);
        UnsafeMemory.free(commaAddr);
        UnsafeMemory.free(quoteAddr);
        UnsafeMemory.free(slashAddr);
        UnsafeMemory.free(ldelimAddr);
        UnsafeMemory.free(rdelimAddr);
    }

    /**
     * Construct the leveled bitmaps by running Stages 1–5 in parallel.
     */
    public LeveledBitmaps construct() throws InterruptedException {
        List<DynamicPartitioner.Partition> parts =
            DynamicPartitioner.partition(json, threads);

        ForkJoinPool pool = new ForkJoinPool(threads);
        try {
            // Stage 1: build raw metachar bitmaps
            pool.invoke(new Stage1Task(parts, 0, parts.size()));

            // Stage 2: remove escaped quotes
            pool.invoke(new Stage2Task(parts, 0, parts.size()));

            // TODO: Stage 3, 4, 5...
        } finally {
            pool.shutdown();
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        }

        // placeholder: after all stages, produce merged LeveledBitmaps
        return new LeveledBitmaps(/* levels, colonArrays, commaArrays */);
    }

    //
    // Stage 1: Build metacharacter bitmaps in parallel.
    //
    private class Stage1Task extends RecursiveTask<Void> {
        private static final int THRESHOLD = 1;
        private final List<DynamicPartitioner.Partition> parts;
        private final int lo, hi;

        Stage1Task(List<DynamicPartitioner.Partition> parts, int lo, int hi) {
            this.parts = parts; this.lo = lo; this.hi = hi;
        }

        @Override
        protected Void compute() {
            if (hi - lo <= THRESHOLD) {
                DynamicPartitioner.Partition p = parts.get(lo);
                buildBitmaps(p.start, p.length);
            } else {
                int mid = (lo + hi) >>> 1;
                invokeAll(
                    new Stage1Task(parts, lo, mid),
                    new Stage1Task(parts, mid, hi)
                );
            }
            return null;
        }

        private void buildBitmaps(int start, int len) {
            int end = start + len;
            int i = start;

            // process 64 bytes per iteration → one 64-bit mask per word
            for (; i + 64 <= end; i += 64) {
                // load first and second 32-byte lanes
                ByteVector v1 = VectorUtils.load(json, i);
                ByteVector v2 = VectorUtils.load(json, i + VectorUtils.SPECIES.length());

                long off = ((long)(i / 64)) * Long.BYTES;

                // ':' bitmap
                long lowColon  = VectorUtils.mask(VectorUtils.eq(v1, (byte)':'));
                long highColon = VectorUtils.mask(VectorUtils.eq(v2, (byte)':'));
                UnsafeMemory.putLong(colonAddr, off, (highColon << 32) | (lowColon & 0xFFFFFFFFL));

                // ',' bitmap
                long lowComma  = VectorUtils.mask(VectorUtils.eq(v1, (byte)','));
                long highComma = VectorUtils.mask(VectorUtils.eq(v2, (byte)','));
                UnsafeMemory.putLong(commaAddr, off, (highComma << 32) | (lowComma & 0xFFFFFFFFL));

                // '"' bitmap
                long lowQuote  = VectorUtils.mask(VectorUtils.eq(v1, (byte)'"'));
                long highQuote = VectorUtils.mask(VectorUtils.eq(v2, (byte)'"'));
                UnsafeMemory.putLong(quoteAddr, off, (highQuote << 32) | (lowQuote & 0xFFFFFFFFL));

                // '\' bitmap
                long lowSlash  = VectorUtils.mask(VectorUtils.eq(v1, (byte)'\\'));
                long highSlash = VectorUtils.mask(VectorUtils.eq(v2, (byte)'\\'));
                UnsafeMemory.putLong(slashAddr, off, (highSlash << 32) | (lowSlash & 0xFFFFFFFFL));

                // '{' bitmap
                long lowL    = VectorUtils.mask(VectorUtils.eq(v1, (byte)'{'));
                long highL   = VectorUtils.mask(VectorUtils.eq(v2, (byte)'{'));
                UnsafeMemory.putLong(ldelimAddr, off, (highL << 32) | (lowL & 0xFFFFFFFFL));

                // '}' bitmap
                long lowR    = VectorUtils.mask(VectorUtils.eq(v1, (byte)'}'));
                long highR   = VectorUtils.mask(VectorUtils.eq(v2, (byte)'}'));
                UnsafeMemory.putLong(rdelimAddr, off, (highR << 32) | (lowR & 0xFFFFFFFFL));
            }

            // tail: handle remaining <64 bytes
            for (int j = i; j < end; j++) {
                byte b = json[j];
                long off = ((long)(j / 64)) * Long.BYTES;
                int bit = j & 63;

                if (b == ':')  writeBit(colonAddr, off, bit);
                if (b == ',')  writeBit(commaAddr, off, bit);
                if (b == '"')  writeBit(quoteAddr, off, bit);
                if (b == '\\') writeBit(slashAddr, off, bit);
                if (b == '{')  writeBit(ldelimAddr, off, bit);
                if (b == '}')  writeBit(rdelimAddr, off, bit);
            }
        }

        private void writeBit(long addr, long off, int bit) {
            long w = UnsafeMemory.getLong(addr, off);
            UnsafeMemory.putLong(addr, off, w | (1L << bit));
        }
    }

    //
    // Stage 2: Remove escaped quotes — clear any '"' bit that follows
    //           an odd-length run of '\' bits.
    //
    private class Stage2Task extends RecursiveAction {
        private static final int THRESHOLD = 1;
        private final List<DynamicPartitioner.Partition> parts;
        private final int lo, hi;

        Stage2Task(List<DynamicPartitioner.Partition> parts, int lo, int hi) {
            this.parts = parts; this.lo = lo; this.hi = hi;
        }

        @Override
        protected void compute() {
            if (hi - lo <= THRESHOLD) {
                DynamicPartitioner.Partition p = parts.get(lo);
                int wOff = p.start / 64;
                int wCnt = (p.length + 63) / 64;
                removeEscapedQuotes(wOff, wCnt);
            } else {
                int mid = (lo + hi) >>> 1;
                invokeAll(
                    new Stage2Task(parts, lo, mid),
                    new Stage2Task(parts, mid, hi)
                );
            }
        }

        private void removeEscapedQuotes(int wOff, int wCnt) {
            for (int w = wOff; w < wOff + wCnt; w++) {
                long slashMask = UnsafeMemory.getLong(slashAddr, w * Long.BYTES);
                long quoteMask = UnsafeMemory.getLong(quoteAddr, w * Long.BYTES);
                long toClear   = computeEscapedQuoteMask(slashMask);
                quoteMask &= ~toClear;
                UnsafeMemory.putLong(quoteAddr, w * Long.BYTES, quoteMask);
            }
        }

        /**
         * Return a mask with 1‑bits at positions of escaped quotes.
         * We detect ends of slash‑runs, pick those of odd length,
         * then shift left by 1 to clear the following '"' bit.
         */
        private long computeEscapedQuoteMask(long slash) {
            long ends    = slash & ~(slash << 1);     // positions where a run ends
            long oddEnds = ends & (~slash ^ ends);    // ends of odd‑length runs
            return oddEnds << 1;                      // the next bit is an escaped '"'
        }
    }


}