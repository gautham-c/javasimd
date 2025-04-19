package com.group8.pison.util;

import sun.misc.Unsafe;

import java.lang.reflect.Field;

/**
 * Minimal wrapper around sun.misc.Unsafe for off‐heap allocation.
 */
public class UnsafeMemory {
    private static final Unsafe UNSAFE;
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Allocate off‐heap memory (in bytes). */
    public static long allocate(long bytes) {
        return UNSAFE.allocateMemory(bytes);
    }

    /** Free off‐heap memory. */
    public static void free(long address) {
        UNSAFE.freeMemory(address);
    }

    /** Write a 64‑bit word at the given address + offset. */
    public static void putLong(long address, long offset, long value) {
        UNSAFE.putLong(address + offset, value);
    }

    /** Read a 64‑bit word from the given address + offset. */
    public static long getLong(long address, long offset) {
        return UNSAFE.getLong(address + offset);
    }
}