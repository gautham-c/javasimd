package com.group8.pison;

import com.group8.pison.index.BitmapConstructor;
import com.group8.pison.index.LeveledBitmaps;
import com.group8.pison.iterator.BitmapIterator;


public class Pison {
    private final LeveledBitmaps bitmaps;

    public Pison(byte[] json, int threads) {
        try {
            BitmapConstructor bc = new BitmapConstructor(json, threads);
            System.out.println("in pison.java"+bc.construct());
            this.bitmaps = bc.construct();
            bc.cleanup();
        } catch (InterruptedException e) {
            // restore interrupt status and wrap
            Thread.currentThread().interrupt();
            throw new RuntimeException("Index construction was interrupted", e);
        }
    }

    public BitmapIterator iterator() {
        return new BitmapIterator(bitmaps);
    }
}