package com.group8.pison.util;

import jdk.incubator.vector.ByteVector;
import jdk.incubator.vector.VectorMask;
import jdk.incubator.vector.VectorSpecies;

/**
 * Helpers for SIMD‐accelerated byte scanning using Java Vector API.
 */
public class VectorUtils {
    // 256‑bit species (32 bytes per vector)
    public static final VectorSpecies<Byte> SPECIES = ByteVector.SPECIES_256;

    /** Compare each byte in 'data' at index to the given value, return mask. */
    public static VectorMask<Byte> eq(ByteVector vector, byte value) {
        return vector.eq((byte) value);
    }

    /** Load a ByteVector from the array (zero‐pad at tail). */
    public static ByteVector load(byte[] data, int offset) {
        return ByteVector.fromArray(SPECIES, data, offset);
    }

    /** Extract a 32‑bit bitmask from the lane‐wise mask. */
    public static long mask(VectorMask<Byte> m) {
        // each lane -> 1 bit
        return m.toLong();
    }
}
