package io.github.jabrena.juno.maven.fixture;

import io.github.jabrena.juno.hardware.ArduinoUno;

import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.util.Delay;

/** Minimal Juno-compilable program: a Mojo test's closed-world classpath entry point, not a build-time example. */
public final class TinyProgram {
    private TinyProgram() {
    }

    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(ArduinoUno.getGpio().getBuiltinLed());
        while (true) {
            led.high();
            Delay.millis(500);
            led.low();
            Delay.millis(500);
        }
    }
}
