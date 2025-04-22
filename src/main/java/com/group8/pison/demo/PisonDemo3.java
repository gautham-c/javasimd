package com.group8.pison.demo;

import com.group8.pison.Pison;
import com.group8.pison.iterator.BitmapIterator;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class PisonDemo3 {
    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        System.out.println("Hello welcome 8");
        byte[] data = Files.readAllBytes(Paths.get("bestbuy_large.json"));
        Pison pison = new Pison(data, 2);
        BitmapIterator it = pison.iterator();

        while (true) {
            System.out.print("Enter field name to access (or 'exit' to quit): ");
            String field = scanner.nextLine();
            if (field.equalsIgnoreCase("exit")) {
                break;
            }

            if (!it.moveToKey(field)) {
                System.out.println("Field not found.");
                return;
            }
            it.skipToValue();

            System.out.println("→ Debug: Checking if current field is array...");
            System.out.println("→ Char at current pos: " + (char) it.getPosChar());
            System.out.println("→ isArray(): " + it.isArray());
            if (it.isArray()) {
                System.out.println("Field is an array.");
                System.out.println("→ Debug: About to call numArrayElements() at pos=" + it.getPos());
                int count = it.numArrayElements();
                System.out.println("→ Debug: numArrayElements returned: " + count);
                System.out.println("→ Debug: Calling it.down() before checking isObject");
                it.down();
                System.out.println("→ Debug: Position after down: " + it.getPos());
                System.out.println("→ Debug: Char at pos=" + it.getPos() + " is '" + it.getPosChar() + "'");
                System.out.println("Array has " + count + " elements.");
                System.out.print("Enter index in array: ");
                int idx = Integer.parseInt(scanner.nextLine());
                System.out.println("→ Debug: Attempting moveToIndex(" + idx + ")");
                if (!it.moveToIndex(idx)) {
                    System.out.println("Invalid index.");
                    return;
                }
                System.out.println("→ Debug: moveToIndex success, pos=" + it.getPos() + " is '" + it.getPosChar() + "'");
                if (it.getPosChar() != '{') {
                    System.out.println("→ Debug: Expected object at index but found '" + it.getPosChar() + "' instead.");
                    return;
                }
                System.out.println("→ Debug: isObject() after moveToIndex: " + it.isObject());
                System.out.println("→ Debug: Evaluating if we need to call it.down()...");
                System.out.println("→ Debug: Position after potential down: " + it.getPos() + " is '" + it.getPosChar() + "'" + " isObject: "+ it.isObject());
                System.out.println("→ Debug: Position after potential down: " + it.getPos() + " is '" + it.getPosChar() + "'" + " isObject: "+ it.isObject());

                if (it.isArray()) {
                    System.out.println("Nested array detected.");
                    System.out.println("→ Debug: Calling it.down() to enter nested array...");
                    it.down();
                    int innerCount = it.numArrayElements();
                    System.out.println("Nested array has " + innerCount + " elements.");
                    System.out.print("Enter inner index: ");
                    int innerIdx = Integer.parseInt(scanner.nextLine());
                    if (!it.moveToIndex(innerIdx)) {
                        System.out.println("Invalid inner index.");
                        return;
                    }
                    System.out.println("→ Debug: Moved to inner index " + innerIdx + ", char: '" + it.getPosChar() + "'");
                }

                if (it.isObject()) {
                    System.out.print("Enter key in object at index " + idx + ": ");
                    String subfield = scanner.nextLine();
                    if (!it.moveToKey(subfield)) {
                        System.out.println("Subfield not found.");
                        return;
                    }
                    System.out.println("Value: " + it.getValue());
                } else {
                    System.out.println("Element at index " + idx + " is not an object.");
                }

                break;
            } else if (it.isObject()) {
                System.out.println("This field is a nested object. Continue navigating or type 'exit'.");
            } else {
                System.out.println("Value: " + it.getValue());
                break;
            }
        }

        scanner.close();
    }
}
