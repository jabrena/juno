package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/** Demonstrates constructing an object and calling instance methods with parameters and return values. */
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

        Serial.print("12 + 5 = ");
        Serial.println(sum);
        Serial.print("17 squared = ");
        Serial.println(squared);

        Serial.print("17 is even: ");
        Serial.println(methods.isEven(sum));

        long product = methods.multiply(123456L, 1000L);
        Serial.print("long product: ");
        Serial.println(product);

        double mean = methods.average(12.5, 7.5);
        Serial.print("average: ");
        Serial.println(mean);

        int combined = methods.addThenDouble(left, right);
        Serial.print("add then double: ");
        Serial.println(combined);
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
