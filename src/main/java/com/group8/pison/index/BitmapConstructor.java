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

            // vectorized scan (32 bytes at a time)
            for (; i + VectorUtils.SPECIES.length() <= end; i += VectorUtils.SPECIES.length()) {
                ByteVector vec = VectorUtils.load(json, i);

                // ':' bitmap
                long mColon = VectorUtils.mask(VectorUtils.eq(vec, (byte) ':'));
                UnsafeMemory.putLong(colonAddr, ((long)i/8)*Long.BYTES, mColon);

                // ',' bitmap
                long mComma = VectorUtils.mask(VectorUtils.eq(vec, (byte) ','));
                UnsafeMemory.putLong(commaAddr, ((long)i/8)*Long.BYTES, mComma);

                // '"' bitmap
                long mQuote = VectorUtils.mask(VectorUtils.eq(vec, (byte) '"'));
                UnsafeMemory.putLong(quoteAddr, ((long)i/8)*Long.BYTES, mQuote);

                // '\' bitmap
                long mSlash = VectorUtils.mask(VectorUtils.eq(vec, (byte) '\\'));
                UnsafeMemory.putLong(slashAddr, ((long)i/8)*Long.BYTES, mSlash);

                // '{' bitmap
                long mL    = VectorUtils.mask(VectorUtils.eq(vec, (byte) '{'));
                UnsafeMemory.putLong(ldelimAddr, ((long)i/8)*Long.BYTES, mL);

                // '}' bitmap
                long mR    = VectorUtils.mask(VectorUtils.eq(vec, (byte) '}'));
                UnsafeMemory.putLong(rdelimAddr, ((long)i/8)*Long.BYTES, mR);
            }

            // tail: byte‐by‐byte
            for (; i < end; i++) {
                byte b = json[i];
                long off = ((long)i/8)*Long.BYTES;
                int bit = i & 63;

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