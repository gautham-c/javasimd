package com.group8.pison.demo;

import com.group8.pison.Pison;
import com.group8.pison.iterator.BitmapIterator;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class PisonDemo4 {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Hello welcome 8");
        byte[] data = Files.readAllBytes(Paths.get("bestbuy_large.json"));
        long startConstruct = System.currentTimeMillis();
        Pison pison = new Pison(data, 2);
        BitmapIterator it = pison.iterator();
        long endConstruct = System.currentTimeMillis();
        System.out.println("Bitmap construction time: " + (endConstruct - startConstruct) + " ms");

        while (true) {
            System.out.print("Enter query (e.g., products[0].shippingLevelsOfService[2].serviceLevelName), or 'exit': ");
            String query = scanner.nextLine();
            if (query.equalsIgnoreCase("exit")) break;

            long startQuery = System.currentTimeMillis();

            it = pison.iterator(); // reset to root

            String[] tokens = query.split("\\.");
            boolean validQuery = true;
            for (String token : tokens) {
                String key = token;
                Integer index = null;

                if (token.contains("[")) {
                    int bracketStart = token.indexOf("[");
                    int bracketEnd = token.indexOf("]");
                    key = token.substring(0, bracketStart);
                    index = Integer.parseInt(token.substring(bracketStart + 1, bracketEnd));
                }

                if (!it.moveToKey(key)) {
                    // System.out.println("Field not found: " + key);
                    validQuery = false;
                    break;
                }

                it.skipToValue();

                if (index != null) {
                    if (!it.isArray()) {
                        // System.out.println("Expected an array at " + key);
                        validQuery = false;
                        break;
                    }

                    it.down();
                    int count = it.numArrayElements();
                    if (index >= count) {
                        // System.out.println("Index " + index + " out of bounds (size: " + count + ")");
                        validQuery = false;
                        break;
                    }

                    if (!it.moveToIndex(index)) {
                        // System.out.println("Failed to move to index " + index);
                        validQuery = false;
                        break;
                    }

                    if (it.isObject()) {
                        // optionally call it.down() if needed for nested object logic
                    }
                }
            }

            if (validQuery) {
                // System.out.println("→ getValue about to be called at pos=" + it.getPos() + " char='" + it.getPosChar() + "'");
                System.out.println("Value: " + it.getValue());
            }

            long endQuery = System.currentTimeMillis();
            System.out.println("Query execution time: " + (endQuery - startQuery) + " ms");
        }

        scanner.close();
    }
}
