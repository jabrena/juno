package io.github.jabrena.juno.m5stack;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5Cardputer;
import io.github.jabrena.juno.hardware.M5;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.util.Delay;

/** Toggles GPIO2 on the Cardputer's HY2.0-4P port once a second. */
@Board(M5Cardputer.class)
public final class M5CardputerGpio {
    private M5CardputerGpio() {
    }

    /** Configures GPIO2 as an output and toggles it forever. */
    public static void main(String[] args) {
        Gpio gpio = M5.getGpio();
        int groveOutput = Gpio.getD2();
        gpio.pinMode(groveOutput, Gpio.getOutput());
        while (true) {
            gpio.digitalWrite(groveOutput, true);
            Delay.millis(1000);
            gpio.digitalWrite(groveOutput, false);
            Delay.millis(1000);
        }
    }
}
