package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/** Demonstrates arithmetic, assignment, comparison, logical, bitwise, shift, and unary operators. */
public class Operators {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        int left = 12;
        int right = 5;

        Serial.println("Using operators demo");

        Serial.println("Arithmetic operators");
        Serial.println("12 + 5 = " + (left + right));
        Serial.println("12 - 5 = " + (left - right));
        Serial.println("12 * 5 = " + (left * right));
        Serial.println("12 / 5 = " + (left / right));
        Serial.println("12 % 5 = " + (left % right));

        Serial.println("Assignment operators");
        int result = left;
        result += right;
        Serial.println("result += 5: " + result);
        result *= 2;
        Serial.println("result *= 2: " + result);

        Serial.println("Comparison operators");
        Serial.println("12 == 5: " + (left == right));
        Serial.println("12 != 5: " + (left != right));
        Serial.println("12 < 5: " + (left < right));
        Serial.println("12 >= 5: " + (left >= right));

        boolean positive = left > 0;
        boolean singleDigit = right < 10;
        Serial.println("Logical operators");
        Serial.println("positive && singleDigit: " + (positive && singleDigit));
        Serial.println("positive || singleDigit: " + (positive || singleDigit));
        Serial.println("!positive: " + (!positive));

        Serial.println("Bitwise operators");
        Serial.println("12 & 5 = " + (left & right));
        Serial.println("12 | 5 = " + (left | right));
        Serial.println("12 ^ 5 = " + (left ^ right));

        Serial.println("Shift operators");
        Serial.println("12 << 2 = " + (left << 2));
        Serial.println("12 >> 1 = " + (left >> 1));
        Serial.println("-16 >>> 2 = " + (-16 >>> 2));

        Serial.println("Unary operators");
        int value = left;
        value++;
        Serial.println("12++ = " + value);
        value--;
        Serial.println("13-- = " + value);
        Serial.println("-12 = " + (-left));
    }
}
