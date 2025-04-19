package com.group8.pison.demo;

import com.group8.pison.Pison;
import com.group8.pison.iterator.BitmapIterator;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Example usage: Query $.user[0].name
 */
public class PisonDemo {
    public static void main(String[] args) throws Exception {
        byte[] data = Files.readAllBytes(Paths.get("twitter.json"));
        Pison pison = new Pison(data, Runtime.getRuntime().availableProcessors());
        BitmapIterator it = pison.iterator();

        if (it.isObject() && it.moveToKey("user")) {
            it.down();
            if (it.isArray() && it.moveToIndex(0)) {
                it.down();
                if (it.isObject() && it.moveToKey("name")) {
                    String name = it.getValue();
                    System.out.println("user[0].name = " + name);
                }
            }
        }
    }
}