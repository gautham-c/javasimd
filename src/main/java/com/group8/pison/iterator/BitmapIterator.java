package com.group8.pison.iterator;

import com.group8.pison.index.LeveledBitmaps;

/**
 * Navigates through leveled bitmaps to answer JSONPath‐style queries.
 */
public class BitmapIterator {
    private final LeveledBitmaps bitmaps;
    private final byte[] json;
    private int level = 0;
    private long pos = 0;  // bit position

    public BitmapIterator(LeveledBitmaps bm, byte[] json) {
        this.bitmaps = bm;
        this.json = json;
    }

    public boolean isObject() {
        int bytePos = (int)(pos);
        if (bytePos < json.length) {
            char c = (char) json[bytePos];
            System.out.println("isObject check at pos=" + bytePos + " char='" + c + "'");
            return c == '{';
        }
        return false;
    }
    
    public boolean isArray() {
        int bytePos = (int)(pos);
        if (bytePos < json.length) {
            char c = (char) json[bytePos];
            System.out.println("isArray check at pos=" + bytePos + " char='" + c + "'");
            return c == '[';
        }
        return false;
    }

    public boolean moveToKey(String key) {
        for (int l = level; l < bitmaps.getEndingLevel(); l++) {
            long nextColon = bitmaps.nextColon(l, pos);
            while (nextColon != -1) {
                int bytePos = (int) nextColon;
                String foundKey = extractKeyBeforeColon(bytePos);
                System.out.println("Checking key at pos " + bytePos + ": " + foundKey);
                if (foundKey.equals(key)) {
                    level = l;
                    pos = nextColon;
                    return true;
                }
                nextColon = bitmaps.nextColon(l, nextColon + 1);
            }
        }
        return false;
    }

    private String extractKeyBeforeColon(int colonBytePos) {
        int i = colonBytePos - 1;

        // Scan backward to find the closing quote of the key
        while (i >= 0 && json[i] != '"') {
            i--;
        }
        int endQuote = i;
        i--;

        // Scan backward to find the opening quote
        while (i >= 0 && json[i] != '"') {
            i--;
        }
        int startQuote = i;

        if (startQuote >= 0 && endQuote > startQuote) {
            return new String(json, startQuote + 1, endQuote - startQuote - 1);
        }

        return "";
    }

    public boolean moveToIndex(int idx) {
        System.out.println("→ moveToIndex(" + idx + ") called at level=" + level + ", pos=" + pos);
        if (idx == 0) {
            System.out.println("→ Index is 0, no movement needed.");
            return true;
        }

        for (int l = 0; l < bitmaps.getEndingLevel(); l++) {
            long tryPos = pos;
            int count = 0;
            System.out.println("→ Trying level " + l + " for commas...");

            while (count < idx) {
                tryPos = bitmaps.nextComma(l, tryPos + 1);
                if (tryPos == -1) {
                    System.out.println("→ No more commas found at level " + l + " after count=" + count);
                    break;
                }
                System.out.println("→ Found comma #" + (count + 1) + " at pos=" + tryPos);
                count++;
            }

            if (count == idx) {
                System.out.println("→ Successfully reached index " + idx + " at level " + l + " with pos=" + tryPos);
                level = l;
                // Advance to the next significant structure
                pos = tryPos + 1;
                while (pos < json.length &&
                      (json[(int) pos] == ' ' || json[(int) pos] == '\n' || json[(int) pos] == '\r' || 
                       json[(int) pos] == '\t' || json[(int) pos] == ',')) {
                    pos++;
                }
                // Skip until reaching the next structure character
                while (pos < json.length && json[(int) pos] != '{' && json[(int) pos] != '[') {
                    pos++;
                }
                return true;
            }
        }

        System.out.println("→ Failed to find index " + idx + " at any level");
        return false;
    }

    public BitmapIterator down() {
        level++;
        pos = bitmaps.rangeStart(level, pos);

        // Skip whitespace to find actual structure
        while (pos < json.length &&
              (json[(int) pos] == ' ' || json[(int) pos] == '\n' || json[(int) pos] == '\r' || json[(int) pos] == '\t')) {
            pos++;
        }

        // If current character is a structure start, stay here
        char c = (char) json[(int) pos];
        if (c != '{' && c != '[') {
            System.out.println("→ Warning: down() landed on unexpected character '" + c + "' at pos=" + pos);
        }

        return this;
    }

    public String getValue() {
        int start = (int)(pos);
        int end = start;
        while (end < json.length && json[end] != ',' && json[end] != '}' && json[end] != ']') {
            end++;
        }
        return new String(json, start, end - start).replace("\"", "").trim();
    }

    public void reset() {
        this.level = 0;
        this.pos = 0;
    }

    // ... moveToIndex, isArray, etc.
    public long getPos() {
        return this.pos;
    }

}
