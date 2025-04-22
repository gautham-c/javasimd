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

public class BitmapConstructor {
    private final long colonAddr, commaAddr, quoteAddr, slashAddr, ldelimAddr, rdelimAddr;
    private final int words;
    private final byte[] json;
    private final int threads;

    public BitmapConstructor(byte[] json, int threads) {
        this.json    = json;
        this.threads = threads;
        this.words   = (json.length + 63) / 64;
        long bytes   = (long) words * Long.BYTES;

        colonAddr  = UnsafeMemory.allocate(bytes);
        commaAddr  = UnsafeMemory.allocate(bytes);
        quoteAddr  = UnsafeMemory.allocate(bytes);
        slashAddr  = UnsafeMemory.allocate(bytes);
        ldelimAddr = UnsafeMemory.allocate(bytes);
        rdelimAddr = UnsafeMemory.allocate(bytes);
    }

    public void cleanup() {
        UnsafeMemory.free(colonAddr);
        UnsafeMemory.free(commaAddr);
        UnsafeMemory.free(quoteAddr);
        UnsafeMemory.free(slashAddr);
        UnsafeMemory.free(ldelimAddr);
        UnsafeMemory.free(rdelimAddr);
    }

    public LeveledBitmaps construct() throws InterruptedException {
        List<DynamicPartitioner.Partition> parts =
            DynamicPartitioner.partition(json, threads);

        ForkJoinPool pool = new ForkJoinPool(threads);
        try {
            pool.invoke(new Stage1Task(parts, 0, parts.size()));

            pool.invoke(new Stage2Task(parts, 0, parts.size()));

            long[] quoteBits = new long[words];
            for (int i = 0; i < words; i++) {
                quoteBits[i] = UnsafeMemory.getLong(quoteAddr, (long)i * Long.BYTES);
            }
            long[] stringMask = new long[words];
            boolean inString = false;
            for (int i = 0; i < words; i++) {
                long quote = quoteBits[i];
                long mask = 0;
                for (int b = 0; b < 64; b++) {
                    if ((quote & (1L << b)) != 0) {
                        inString = !inString;
                    }
                    if (inString) {
                        mask |= (1L << b);
                    }
                }
                stringMask[i] = mask;
            }

            for (int i = 0; i < words; i++) {
                long sm = ~stringMask[i];
                UnsafeMemory.putLong(colonAddr, (long)i * Long.BYTES, UnsafeMemory.getLong(colonAddr, (long)i * Long.BYTES) & sm);
                UnsafeMemory.putLong(commaAddr, (long)i * Long.BYTES, UnsafeMemory.getLong(commaAddr, (long)i * Long.BYTES) & sm);
                UnsafeMemory.putLong(ldelimAddr, (long)i * Long.BYTES, UnsafeMemory.getLong(ldelimAddr, (long)i * Long.BYTES) & sm);
                UnsafeMemory.putLong(rdelimAddr, (long)i * Long.BYTES, UnsafeMemory.getLong(rdelimAddr, (long)i * Long.BYTES) & sm);
            }

            int maxLevels = 16;
            long[][] colonLevels = new long[maxLevels][words];
            long[][] commaLevels = new long[maxLevels][words];

            int level = 0;
            for (int i = 0; i < json.length; i++) {
                int wordIndex = i / 64;
                int bitOffset = i % 64;
                byte b = json[i];

                if (b == ':' || b == ',') {
                    long mask = UnsafeMemory.getLong(b == ':' ? colonAddr : commaAddr, wordIndex * Long.BYTES);
                if (((mask >> bitOffset) & 1) != 0 && level < maxLevels) {
                    if (b == ':') colonLevels[level][wordIndex] |= (1L << bitOffset);
                    else commaLevels[level][wordIndex] |= (1L << bitOffset);
                }
                }

                if (b == '{' || b == '[') {
                    if (level < maxLevels - 1) level++;
                } else if (b == '}' || b == ']') {
                    level = Math.max(0, level - 1);
                }
            }

            int actualLevels = 0;
            for (int l = 0; l < maxLevels; l++) {
                for (int w = 0; w < words; w++) {
                    if (colonLevels[l][w] != 0 || commaLevels[l][w] != 0) {
                        actualLevels = l + 1;
                        break;
                    }
                }
            }

            long[][] finalColon = new long[actualLevels][];
            long[][] finalComma = new long[actualLevels][];
            System.arraycopy(colonLevels, 0, finalColon, 0, actualLevels);
            System.arraycopy(commaLevels, 0, finalComma, 0, actualLevels);

            return new LeveledBitmaps(actualLevels, finalColon, finalComma);
        } finally {
            pool.shutdown();
            pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        }
    }

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

            for (; i + 64 <= end; i += 64) {
                ByteVector v1 = VectorUtils.load(json, i);
                ByteVector v2 = VectorUtils.load(json, i + VectorUtils.SPECIES.length());

                long off = ((long)(i / 64)) * Long.BYTES;

                long lowColon  = VectorUtils.mask(VectorUtils.eq(v1, (byte)':'));
                long highColon = VectorUtils.mask(VectorUtils.eq(v2, (byte)':'));
                UnsafeMemory.putLong(colonAddr, off, (highColon << 32) | (lowColon & 0xFFFFFFFFL));

                long lowComma  = VectorUtils.mask(VectorUtils.eq(v1, (byte)','));
                long highComma = VectorUtils.mask(VectorUtils.eq(v2, (byte)','));
                UnsafeMemory.putLong(commaAddr, off, (highComma << 32) | (lowComma & 0xFFFFFFFFL));

                long lowQuote  = VectorUtils.mask(VectorUtils.eq(v1, (byte)'"'));
                long highQuote = VectorUtils.mask(VectorUtils.eq(v2, (byte)'"'));
                UnsafeMemory.putLong(quoteAddr, off, (highQuote << 32) | (lowQuote & 0xFFFFFFFFL));

                long lowSlash  = VectorUtils.mask(VectorUtils.eq(v1, (byte)'\\'));
                long highSlash = VectorUtils.mask(VectorUtils.eq(v2, (byte)'\\'));
                UnsafeMemory.putLong(slashAddr, off, (highSlash << 32) | (lowSlash & 0xFFFFFFFFL));

                long lowL    = VectorUtils.mask(VectorUtils.eq(v1, (byte)'{'));
                long highL   = VectorUtils.mask(VectorUtils.eq(v2, (byte)'{'));
                UnsafeMemory.putLong(ldelimAddr, off, (highL << 32) | (lowL & 0xFFFFFFFFL));

                long lowR    = VectorUtils.mask(VectorUtils.eq(v1, (byte)'}'));
                long highR   = VectorUtils.mask(VectorUtils.eq(v2, (byte)'}'));
                UnsafeMemory.putLong(rdelimAddr, off, (highR << 32) | (lowR & 0xFFFFFFFFL));
            }

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

        private long computeEscapedQuoteMask(long slash) {
            long ends    = slash & ~(slash << 1);
            long oddEnds = ends & (~slash ^ ends);
            return oddEnds << 1;
        }
    }


}