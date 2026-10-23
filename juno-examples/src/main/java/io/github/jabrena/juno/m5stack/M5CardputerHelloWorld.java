package io.github.jabrena.juno.m5stack;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/** Prints a message over the M5Stack Cardputer's USB serial connection for {@code juno:monitor}. */
@Board(io.github.jabrena.juno.annotations.M5StackCardputer.class)
public final class M5CardputerHelloWorld {
    private M5CardputerHelloWorld() {
    }

    /** Starts serial output and repeats the message so the monitor can attach after startup. */
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        while (true) {
            Serial.println("Hello, World!");
            Delay.millis(1000);
        }
    }
}
