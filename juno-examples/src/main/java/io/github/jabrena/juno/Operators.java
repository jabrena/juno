package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/** Demonstrates arithmetic, assignment, comparison, logical, bitwise, shift, and unary operators. */
public final class Operators {
    private final int left = 12;
    private final int right = 5;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Operators operators = new Operators();

        Serial.println("Using operators demo");

        operators.arithmetic();
        operators.assignment();
        operators.comparison();
        operators.logical();
        operators.bitwise();
        operators.shifts();
        operators.unary();
    }

    private void arithmetic() {
        Serial.println("Arithmetic operators");
        Serial.println("12 + 5 = " + (left + right));
        Serial.println("12 - 5 = " + (left - right));
        Serial.println("12 * 5 = " + (left * right));
        Serial.println("12 / 5 = " + (left / right));
        Serial.println("12 % 5 = " + (left % right));
    }

    private void assignment() {
        Serial.println("Assignment operators");
        int result = left;
        result += right;
        Serial.println("result += 5: " + result);
        result *= 2;
        Serial.println("result *= 2: " + result);
    }

    private void comparison() {
        Serial.println("Comparison operators");
        Serial.println("12 == 5: " + (left == right));
        Serial.println("12 != 5: " + (left != right));
        Serial.println("12 < 5: " + (left < right));
        Serial.println("12 >= 5: " + (left >= right));
    }

    private void logical() {
        boolean positive = left > 0;
        boolean singleDigit = right < 10;
        Serial.println("Logical operators");
        Serial.println("positive && singleDigit: " + (positive && singleDigit));
        Serial.println("positive || singleDigit: " + (positive || singleDigit));
        Serial.println("!positive: " + (!positive));
    }

    private void bitwise() {
        Serial.println("Bitwise operators");
        Serial.println("12 & 5 = " + (left & right));
        Serial.println("12 | 5 = " + (left | right));
        Serial.println("12 ^ 5 = " + (left ^ right));
    }

    private void shifts() {
        Serial.println("Shift operators");
        Serial.println("12 << 2 = " + (left << 2));
        Serial.println("12 >> 1 = " + (left >> 1));
        Serial.println("-16 >>> 2 = " + (-16 >>> 2));
    }

    private void unary() {
        Serial.println("Unary operators");
        int value = left;
        value++;
        Serial.println("12++ = " + value);
        value--;
        Serial.println("13-- = " + value);
        Serial.println("-12 = " + (-left));
    }
}
