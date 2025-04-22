package com.group8.pison;

import com.group8.pison.index.BitmapConstructor;
import com.group8.pison.index.LeveledBitmaps;
import com.group8.pison.iterator.BitmapIterator;

public class Pison {
    private final LeveledBitmaps bitmaps;
    private final byte[] json;

    public Pison(byte[] json, int threads) {
        try {
            this.json = json;
            BitmapConstructor bc = new BitmapConstructor(json, threads);
            LeveledBitmaps bm = bc.construct();        // < run only once
            this.bitmaps = bm;
            bc.cleanup();
        } catch (InterruptedException e) {
            // restore interrupt status and wrap
            Thread.currentThread().interrupt();
            throw new RuntimeException("Index construction was interrupted", e);
        }
    }

    public BitmapIterator iterator() {
        return new BitmapIterator(bitmaps, json);
    }

    public LeveledBitmaps getBitmaps() {
        return this.bitmaps;
    }
}