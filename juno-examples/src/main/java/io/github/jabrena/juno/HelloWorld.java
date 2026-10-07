package io.github.jabrena.juno;

import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;

public class HelloWorld {
    public static void main(String[] args) {
        // System.out.println("Hello, World!");
        Serial.begin(BaudRate.BAUD_115200);
        Serial.println("Hello, World!");
    }
}
