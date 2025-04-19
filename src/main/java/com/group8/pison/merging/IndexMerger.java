package com.group8.pison.merging;

import com.group8.pison.index.LeveledBitmaps;

/**
 * Merges per‐chunk leveled bitmaps into global bitmaps.
 */
public class IndexMerger {
    /**
     * Given partial per‐chunk LeveledBitmaps,
     * adjust levels by prefix‐summing endLevels, then link them.
     */
    public static LeveledBitmaps merge(LeveledBitmaps[] partials) {
        // 1) compute prefix sum of ending levels
        int offset = 0;
        for (LeveledBitmaps p : partials) {
            p.shiftLevels(offset);
            offset += p.getEndingLevel();
        }
        // 2) concatenate bitmaps in level‐buckets
        return LeveledBitmaps.concat(partials);
    }
}