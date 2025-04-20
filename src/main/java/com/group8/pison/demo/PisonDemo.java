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
        String jsonPath = args.length > 0 ? args[0] : "sample.json";
        System.out.println("Loading JSON from: " + jsonPath);
        byte[] data = Files.readAllBytes(Paths.get(jsonPath));
        System.out.println("printing data:");
        System.out.println(data);
        Pison pison = new Pison(data, Runtime.getRuntime().availableProcessors());
        System.out.println("testing 2");
        BitmapIterator it = pison.iterator();
        System.out.println("DEBUG:");

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
            if (it.isArray() && it.moveToIndex(0)) {
                System.out.println("if 2");
                it.down();
                if (it.isObject() && it.moveToKey("name")) {
                    String name = it.getValue();
                    System.out.println("user[0].name = " + name);
                }
            }
        }
    }
}