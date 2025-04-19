package com.group8.pison.partition;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits the JSON byte array into partitions, avoiding
 * cutting in the middle of backslash sequences or keywords.
 */
public class DynamicPartitioner {
    public static class Partition {
        public final int start, length;
        public Partition(int s, int l) { start = s; length = l;}
    }

    /**
     * Partition raw JSON into up to 'threads' chunks.
     */
    public static List<Partition> partition(byte[] json, int threads) {
        int n = json.length;
        int base = n / threads;
        List<Partition> parts = new ArrayList<>(threads);
        int cursor = 0;
        for (int t = 0; t < threads; t++) {
            int targetEnd = (t == threads-1) ? n : cursor + base;
            // back off to avoid splitting '\\'+
            while (targetEnd < n && json[targetEnd-1] == '\\') {
                targetEnd--;
            }
            parts.add(new Partition(cursor, targetEnd - cursor));
            cursor = targetEnd;
        }
        return parts;
    }
}