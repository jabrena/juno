package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/** Demonstrates strings: literals, length, charAt, equals, {@code String.valueOf}, concatenation, and a StringBuilder. */
public class Strings {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Serial.println("Strings demo");

        String name = "Juno";
        Serial.println("name: " + name);
        Serial.println("length: " + name.length());
        Serial.println("first: " + name.charAt(0));
        Serial.println("last: " + name.charAt(name.length() - 1));
        Serial.println("equals Juno: " + name.equals("Juno"));
        Serial.println("equals Uno: " + name.equals("Uno"));

        int count = 42;
        String text = String.valueOf(count);
        Serial.println("valueOf: " + text + " has " + text.length() + " digits");
        Serial.println("greeting: " + "Hello, " + name + "! count=" + count);

        Serial.println("reversed: " + reverse(name));
    }

    private static String reverse(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = value.length() - 1; index >= 0; index--) {
            builder.append(value.charAt(index));
        }
        return builder.toString();
    }
}
