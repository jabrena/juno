package io.github.jabrena.juno.language;

import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/** The smallest Juno program: write one line to the board's serial console. */
public final class HelloWorld {

    private HelloWorld() {
    }

    public static void main() {
        Serial.begin(BaudRate.BAUD_115200);
        Serial.println("Hello World");
    }
}
