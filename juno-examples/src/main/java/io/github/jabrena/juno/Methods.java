package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/**
 * Demonstrates constructing an object and calling instance methods with parameters and return values,
 * reporting each result with string concatenation.
 */
public final class Methods {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Methods methods = new Methods();
        methods.printHeading();

        int left = 12;
        int right = 5;
        int sum = methods.add(left, right);
        int squared = methods.square(sum);

        Serial.println(left + " + " + right + " = " + sum);
        Serial.println(sum + " squared = " + squared);
        Serial.println(sum + " is even: " + methods.isEven(sum));

        long product = methods.multiply(123456L, 1000L);
        Serial.println("long product: " + product);

        double mean = methods.average(12.5, 7.5);
        Serial.println("average: " + mean);

        int combined = methods.addThenDouble(left, right);
        Serial.println("add then double: " + combined);
    }

    private void printHeading() {
        Serial.println("Defining methods demo");
    }

    private int add(int first, int second) {
        return first + second;
    }

    private int square(int value) {
        return value * value;
    }

    private boolean isEven(int value) {
        return value % 2 == 0;
    }

    private long multiply(long first, long second) {
        return first * second;
    }

    private double average(double first, double second) {
        return (first + second) / 2.0;
    }

    private int addThenDouble(int first, int second) {
        return doubleValue(add(first, second));
    }

    private int doubleValue(int value) {
        return value * 2;
    }

}
