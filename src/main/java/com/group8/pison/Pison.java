package com.group8.pison;

import com.group8.pison.index.BitmapConstructor;
import com.group8.pison.index.LeveledBitmaps;
import com.group8.pison.iterator.BitmapIterator;

/**
 * Facade API: construct indices and produce an iterator.
 */
public class Pison {
    private final LeveledBitmaps bitmaps;

    public Pison(byte[] json, int threads) {
        BitmapConstructor bc = new BitmapConstructor(json, threads);
        this.bitmaps = bc.construct();
        bc.cleanup();
    }

    public BitmapIterator iterator() {
        return new BitmapIterator(bitmaps);
    }
}