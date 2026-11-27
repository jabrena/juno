package ${package};

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.util.Delay;

public final class Blink {
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
