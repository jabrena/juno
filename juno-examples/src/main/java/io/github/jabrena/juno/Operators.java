package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/** Demonstrates arithmetic, assignment, comparison, logical, bitwise, shift, and unary operators. */
public final class Operators {
    private Operators() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        int left = 12;
        int right = 5;

        Serial.println("Using operators demo");

        Serial.println("Arithmetic operators");
        Serial.print("12 + 5 = ");
        Serial.println(left + right);
        Serial.print("12 - 5 = ");
        Serial.println(left - right);
        Serial.print("12 * 5 = ");
        Serial.println(left * right);
        Serial.print("12 / 5 = ");
        Serial.println(left / right);
        Serial.print("12 % 5 = ");
        Serial.println(left % right);

        Serial.println("Assignment operators");
        int result = left;
        result += right;
        Serial.print("result += 5: ");
        Serial.println(result);
        result *= 2;
        Serial.print("result *= 2: ");
        Serial.println(result);

        Serial.println("Comparison operators");
        Serial.print("12 == 5: ");
        printBoolean(left == right);
        Serial.print("12 != 5: ");
        printBoolean(left != right);
        Serial.print("12 < 5: ");
        printBoolean(left < right);
        Serial.print("12 >= 5: ");
        printBoolean(left >= right);

        boolean positive = left > 0;
        boolean singleDigit = right < 10;
        Serial.println("Logical operators");
        Serial.print("positive && singleDigit: ");
        printBoolean(positive && singleDigit);
        Serial.print("positive || singleDigit: ");
        printBoolean(positive || singleDigit);
        Serial.print("!positive: ");
        printBoolean(!positive);

        Serial.println("Bitwise operators");
        Serial.print("12 & 5 = ");
        Serial.println(left & right);
        Serial.print("12 | 5 = ");
        Serial.println(left | right);
        Serial.print("12 ^ 5 = ");
        Serial.println(left ^ right);

        Serial.println("Shift operators");
        Serial.print("12 << 2 = ");
        Serial.println(left << 2);
        Serial.print("12 >> 1 = ");
        Serial.println(left >> 1);
        Serial.print("-16 >>> 2 = ");
        Serial.println(-16 >>> 2);

        Serial.println("Unary operators");
        int value = left;
        value++;
        Serial.print("12++ = ");
        Serial.println(value);
        value--;
        Serial.print("13-- = ");
        Serial.println(value);
        Serial.print("-12 = ");
        Serial.println(-left);
    }

    private static void printBoolean(boolean value) {
        if (value) {
            Serial.println("true");
        } else {
            Serial.println("false");
        }
    }
}
