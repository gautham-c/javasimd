package com.group8.pison.speculation;

import com.group8.pison.util.UnsafeMemory;

/**
 * Handles speculative processing and bitwise rectification.
 */
public class SpeculativeProcessor {
    /**
     * Flip all bits in the bitmap region [0..words).
     */
    public static void rectify(long address, int words) {
        for (int i = 0; i < words; i++) {
            long w = UnsafeMemory.getLong(address, i * Long.BYTES);
            UnsafeMemory.putLong(address, i * Long.BYTES, ~w);
        }
    }
}