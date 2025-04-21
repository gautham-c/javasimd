package com.group8.pison.demo;

import com.group8.pison.Pison;
import com.group8.pison.iterator.BitmapIterator;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

/**
 * Example usage: Query $.user[1].name
 */
public class PisonDemo {
    public static void main(String[] args) throws Exception {
        String jsonPath = args.length > 0 ? args[0] : "sample.json";
        System.out.println("Loading JSON from: " + jsonPath);
        byte[] data = Files.readAllBytes(Paths.get(jsonPath));
        Pison pison = new Pison(data, Runtime.getRuntime().availableProcessors());
        BitmapIterator it = pison.iterator();
        
        System.out.println("LeveledBitmaps levels: " + pison.getBitmaps().getEndingLevel());
        
        if (it.isObject()) {
            // root is object, stay at root
        } else if (it.isArray()) {
            System.out.println("Root is array, entering first element");
            it.down();
            it.moveToIndex(0);
            it.down();
        } else {
            System.out.println("Unsupported root type");
            return;
        }
        if (it.isObject() && it.moveToKey("user")) {
            System.out.println("if 1");
            it.down();
            if (it.isArray() && it.moveToIndex(1)) {
                System.out.println("if 2");
                System.out.println("→ At user[1], pos=" + it.getPos());
                if (it.isObject()) {
                    System.out.println("→ Confirmed object, checking for key 'name'...");
                    if (it.moveToKey("name")) {
                        String name = it.getValue();
                        System.out.println("user[1].name = " + name);
                    } else {
                        System.out.println("→ Key 'name' not found inside user[1]");
                    }
                } else {
                    System.out.println("→ user[1] is not an object");
                }
            }
        } else {
            System.out.println("Key 'user[1].name' not found via index traversal.");
        }
    }
}