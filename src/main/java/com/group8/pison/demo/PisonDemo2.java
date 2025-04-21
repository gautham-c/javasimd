package com.group8.pison.demo;

import com.group8.pison.Pison;
import com.group8.pison.iterator.BitmapIterator;

import java.nio.file.Files;
import java.nio.file.Paths;

public class PisonDemo2 {
    public static void main(String[] args) throws Exception {
        String jsonPath = args.length > 0 ? args[0] : "sample.json";
        byte[] data = Files.readAllBytes(Paths.get(jsonPath));
        Pison pison = new Pison(data, Runtime.getRuntime().availableProcessors());
        BitmapIterator it = pison.iterator();

        if (it.isObject() && it.moveToKey("user")) {
            it.down();
            if (it.isArray() && it.moveToIndex(0)) {
                it.down();
                if (it.isObject() && it.moveToKey("name")) {
                    String name = it.getValue();
                    System.out.println(name);
                } else {
                    System.out.println("Could not find key 'name'");
                }
            }
        }
    }
}
