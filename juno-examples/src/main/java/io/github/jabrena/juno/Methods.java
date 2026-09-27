package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/** Demonstrates defining and calling static methods with parameters and return values. */
public final class Methods {
    private Methods() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        printHeading();

        int left = 12;
        int right = 5;
        int sum = add(left, right);
        int squared = square(sum);

        Serial.print("12 + 5 = ");
        Serial.println(sum);
        Serial.print("17 squared = ");
        Serial.println(squared);

        Serial.print("17 is even: ");
        printBoolean(isEven(sum));

        long product = multiply(123456L, 1000L);
        Serial.print("long product: ");
        Serial.println(product);

        double mean = average(12.5, 7.5);
        Serial.print("average: ");
        Serial.println(mean);

        int combined = addThenDouble(left, right);
        Serial.print("add then double: ");
        Serial.println(combined);
    }

    private static void printHeading() {
        Serial.println("Defining methods demo");
    }

    private static int add(int first, int second) {
        return first + second;
    }

    private static int square(int value) {
        return value * value;
    }

    private static boolean isEven(int value) {
        return value % 2 == 0;
    }

    private static long multiply(long first, long second) {
        return first * second;
    }

    private static double average(double first, double second) {
        return (first + second) / 2.0;
    }

    private static int addThenDouble(int first, int second) {
        return doubleValue(add(first, second));
    }

    private static int doubleValue(int value) {
        return value * 2;
    }

    private static void printBoolean(boolean value) {
        if (value) {
            Serial.println("true");
        } else {
            Serial.println("false");
        }
    }
}
