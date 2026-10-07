package io.github.jabrena.juno;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

/** Demonstrates strings: literals, length, charAt, equals, {@code String.valueOf}, concatenation, and a StringBuilder. */
public final class Strings {
    private final String name = "Juno";
    private final int count = 42;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Strings strings = new Strings();

        Serial.println("Strings demo");

        strings.inspecting();
        strings.comparing();
        strings.converting();
        strings.concatenating();
        strings.reversing();
    }

    private void inspecting() {
        Serial.println("name: " + name);
        Serial.println("length: " + name.length());
        Serial.println("first: " + name.charAt(0));
        Serial.println("last: " + name.charAt(name.length() - 1));
    }

    private void comparing() {
        Serial.println("equals Juno: " + name.equals("Juno"));
        Serial.println("equals Uno: " + name.equals("Uno"));
    }

    private void converting() {
        String text = String.valueOf(count);
        Serial.println("valueOf: " + text + " has " + text.length() + " digits");
    }

    private void concatenating() {
        Serial.println("greeting: " + "Hello, " + name + "! count=" + count);
    }

    private void reversing() {
        Serial.println("reversed: " + reverse(name));
    }

    private String reverse(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int index = value.length() - 1; index >= 0; index--) {
            builder.append(value.charAt(index));
        }
        return builder.toString();
    }
}
