package com.group8.pison.demo;

import com.group8.pison.Pison;
import com.group8.pison.iterator.BitmapIterator;

import java.nio.file.Files;
import java.nio.file.Paths;

public class PisonDemo3 {
    public static void main(String[] args) throws Exception {
        String jsonPath = "bestbuy.json";
        byte[] data = Files.readAllBytes(Paths.get(jsonPath));
        Pison pison = new Pison(data, 2);
        BitmapIterator it = pison.iterator();

        if (it.isObject()) {
            if (it.moveToKey("name")) {
                System.out.println("Name: " + it.getValue());
            }
            if (it.moveToKey("salePrice")) {
                System.out.println("Sale Price: " + it.getValue());
            }
            if (it.moveToKey("artistName")) {
                System.out.println("Artist: " + it.getValue());
            }
        }
    }
}
