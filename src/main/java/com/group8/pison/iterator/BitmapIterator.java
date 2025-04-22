package com.group8.pison.iterator;

import com.group8.pison.index.LeveledBitmaps;

public class BitmapIterator {
    private final LeveledBitmaps bitmaps;
    private final byte[] json;
    private int level = 0;
    private long pos = 0;

    public BitmapIterator(LeveledBitmaps bm, byte[] json) {
        this.bitmaps = bm;
        this.json = json;
    }

    public boolean isObject() {
        int bytePos = (int)(pos);
        if (bytePos < json.length) {
            char c = (char) json[bytePos];
            
            return c == '{';
        }
        return false;
    }
    
    public boolean isArray() {
        int bytePos = (int)(pos);
        if (bytePos < json.length) {
            char c = (char) json[bytePos];
            
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
        if (idx == 0) {
            return true;
        }

        long arrayStart = pos;
        long arrayEnd = json.length;

        long tryPos = arrayStart;
        int count = 0;

        while (count < idx) {
            tryPos = bitmaps.nextComma(level, tryPos + 1);
            if (tryPos == -1 || tryPos >= arrayEnd) {
                return false;
            }
            count++;
        }

        if (count == idx) {
            pos = tryPos + 1;
            while (pos < json.length &&
                  (json[(int) pos] == ' ' || json[(int) pos] == '\n' || json[(int) pos] == '\r' ||
                   json[(int) pos] == '\t' || json[(int) pos] == ',')) {
                pos++;
            }
            while (pos < json.length && json[(int) pos] != '{' && json[(int) pos] != '[') {
                pos++;
            }
            return true;
        }

        return false;
    }

    public BitmapIterator down() {
        level++;
        pos = bitmaps.rangeStart(level, pos);

        while (pos < json.length &&
              (json[(int) pos] == ' ' || json[(int) pos] == '\n' || json[(int) pos] == '\r' || json[(int) pos] == '\t')) {
            pos++;
        }

        char c = (char) json[(int) pos];
        if (c != '{' && c != '[') {
            
        }

        return this;
    }

    public String getValue() {
        int start = (int) pos;
        if (json[start] == ':') {
            start++;
            while (start < json.length &&
                  (json[start] == ' ' || json[start] == '\n' || json[start] == '\r' || json[start] == '\t')) {
                start++;
            }
        }
        char firstChar = (char) json[start];

        if (firstChar == '{' || firstChar == '[') {
            int depth = 0;
            char open = firstChar;
            char close = (firstChar == '{') ? '}' : ']';
            int i = start;

            while (i < json.length) {
                char c = (char) json[i];
                if (c == open) depth++;
                else if (c == close) depth--;
                if (depth == 0) break;
                i++;
            }

            int end = i + 1;
            return new String(json, start, end - start).trim();
        }

        // Handle scalar value (string, number, etc.)
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

    
    public long getPos() {
        return this.pos;
    }

    public int numArrayElements() {
        int count = 1;
        long nextComma = bitmaps.nextComma(level, pos);
        while (nextComma != -1) {
            count++;
            nextComma = bitmaps.nextComma(level, nextComma + 1);
        }
        return count;
    }


    public char getPosChar() {
        return (char) json[(int) pos];
    }

    public void skipToValue() {
        int p = (int) pos;
        if (json[p] == ':') {
            p++;
            while (p < json.length && (json[p] == ' ' || json[p] == '\n' || json[p] == '\r' || json[p] == '\t')) {
                p++;
            }
            pos = p;
        }
    }

}
