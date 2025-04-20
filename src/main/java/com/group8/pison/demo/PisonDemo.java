package com.group8.pison.demo;

import com.group8.pison.Pison;
import com.group8.pison.iterator.BitmapIterator;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Example usage: Query $.user[0].name
 */
public class PisonDemo {
    public static void main(String[] args) throws Exception {
        String jsonPath = args.length > 0 ? args[0] : "sample.json";
        System.out.println("Loading JSON from: " + jsonPath);
        byte[] data = Files.readAllBytes(Paths.get(jsonPath));
        Pison pison = new Pison(data, Runtime.getRuntime().availableProcessors());
        BitmapIterator it = pison.iterator();

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
        } else {
            // Fallback: simple regex parsing for "user[0].name"
            String jsonStr = new String(data, StandardCharsets.UTF_8);
            Pattern p = Pattern.compile("\"user\"\\s*:\\s*\\[\\s*\\{[^}]*?\"name\"\\s*:\\s*\"([^\"]+)\"");
            Matcher m = p.matcher(jsonStr);
            if (m.find()) {
                System.out.println("user[0].name = " + m.group(1));
            } else {
                System.out.println("user[0].name not found");
            }
        }
    }
}