package com.group8.pison.index;

import java.util.Arrays;

/**
 * Container for the final leveled colon/comma bitmaps.
 * Supports shifting levels and concatenating per‐chunk results.
 */
public class LeveledBitmaps {
    private int levels;              // number of levels
    private final long[][] colon;    // colon[level][wordIndex]
    private final long[][] comma;    // comma[level][wordIndex]
    private final int words;         // number of 64‑bit words per level

    /**
     * Construct from explicit arrays.
     *
     * @param levels   number of levels
     * @param colon    colon bitmaps per level
     * @param comma    comma bitmaps per level
     */
    public LeveledBitmaps(int levels, long[][] colon, long[][] comma) {
        this.levels = levels;
        this.colon  = colon;
        this.comma  = comma;
        this.words  = colon[0].length;
    }
    public LeveledBitmaps() {
        this.levels = 0;
        this.words = 0;
        this.colon  = new long[0][0];
        this.comma  = new long[0][0];
    }

    /** Number of levels (and also the “ending level”). */
    public int getEndingLevel() {
        return levels;
    }

    /**
     * Shift all levels by the given offset.
     * After this, former level 0 becomes level=offset.
     */
    public void shiftLevels(int offset) {
        int newLevels = offset + levels;
        long[][] newColon = new long[newLevels][words];
        long[][] newComma = new long[newLevels][words];
        // existing levels [0..levels) → [offset..offset+levels)
        for (int lvl = 0; lvl < levels; lvl++) {
            System.arraycopy(colon[lvl], 0, newColon[offset + lvl], 0, words);
            System.arraycopy(comma[lvl], 0, newComma[offset + lvl], 0, words);
        }
        // swap in
        levels = newLevels;
        for (int i = 0; i < levels; i++) {
            colon[i] = newColon[i];
            comma[i] = newComma[i];
        }
    }

    /**
     * Merge multiple per‐chunk LeveledBitmaps into one global one.
     * Parts must already have been shiftLevels(...)‑ed appropriately.
     */
    public static LeveledBitmaps concat(LeveledBitmaps[] parts) {
        // find total levels
        int maxLevels = 0;
        for (LeveledBitmaps p : parts) {
            maxLevels = Math.max(maxLevels, p.levels);
        }
        // total words = sum of words per part
        int totalWords = Arrays.stream(parts).mapToInt(p -> p.words).sum();

        // allocate global bitmaps
        long[][] gColon = new long[maxLevels][totalWords];
        long[][] gComma = new long[maxLevels][totalWords];

        int wordOffset = 0;
        for (LeveledBitmaps p : parts) {
            for (int lvl = 0; lvl < maxLevels; lvl++) {
                long[] srcColon = lvl < p.levels ? p.colon[lvl] : new long[p.words];
                long[] srcComma = lvl < p.levels ? p.comma[lvl] : new long[p.words];
                System.arraycopy(srcColon, 0, gColon[lvl], wordOffset, p.words);
                System.arraycopy(srcComma, 0, gComma[lvl], wordOffset, p.words);
            }
            wordOffset += p.words;
        }

        return new LeveledBitmaps(maxLevels, gColon, gComma);
    }

    /**
     * Find the next colon bit at or after bit‐position pos in the given level.
     * @return absolute bit index, or –1 if none.
     */
    public long nextColon(int level, long pos) {
        if (level < 0 || level >= levels) return -1;
        int wIdx = (int)(pos >>> 6);       // divide by 64
        int bOff = (int)(pos & 63L);       // pos % 64
        long[] levelBits = colon[level];

        // mask off lower bits in first word
        long w = levelBits[wIdx] & (~0L << bOff);
        if (w != 0) {
            return ((long)wIdx << 6) + Long.numberOfTrailingZeros(w);
        }
        // scan remaining words
        for (int i = wIdx + 1; i < words; i++) {
            if (levelBits[i] != 0) {
                return ((long)i << 6) + Long.numberOfTrailingZeros(levelBits[i]);
            }
        }
        return -1;
    }

    /**
     * Find the next comma bit at or after bit‐position pos in the given level.
     * @return absolute bit index, or –1 if none.
     */
    public long nextComma(int level, long pos) {
        if (level < 0 || level >= levels) return -1;
        int wIdx = (int)(pos >>> 6);
        int bOff = (int)(pos & 63L);
        long[] levelBits = comma[level];

        long w = levelBits[wIdx] & (~0L << bOff);
        if (w != 0) {
            return ((long)wIdx << 6) + Long.numberOfTrailingZeros(w);
        }
        for (int i = wIdx + 1; i < words; i++) {
            if (levelBits[i] != 0) {
                return ((long)i << 6) + Long.numberOfTrailingZeros(levelBits[i]);
            }
        }
        return -1;
    }

    /**
     * After you locate a colon at bit pos, the corresponding field’s value
     * starts at pos+1.
     */
    public long rangeStart(int level, long pos) {
        return pos + 1;
    }
}
