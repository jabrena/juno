package io.github.jabrena.juno.maven.fixture;

import io.github.jabrena.juno.api.hardware.MCU;

import io.github.jabrena.juno.api.io.gpio.DigitalOutput;
import io.github.jabrena.juno.api.util.Delay;

/** Minimal Juno-compilable program: a Mojo test's closed-world classpath entry point, not a build-time example. */
public final class TinyProgram {
    private TinyProgram() {
    }

    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(MCU.getGpio().getBuiltinLed());
        while (true) {
            led.high();
            Delay.millis(500);
            led.low();
            Delay.millis(500);
        }
    }
}
