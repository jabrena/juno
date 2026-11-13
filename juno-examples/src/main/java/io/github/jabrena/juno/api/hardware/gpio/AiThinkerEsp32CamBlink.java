package io.github.jabrena.juno.api.hardware.gpio;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.gpio.DigitalOutput;
import io.github.jabrena.juno.util.Delay;

/** Blinks the AI Thinker ESP32-CAM's red status LED on GPIO33. */
@Board(AiThinkerEsp32CamBoard.class)
public final class AiThinkerEsp32CamBlink {
    private static final int BLINK_DELAY_MILLIS = 500;

    private AiThinkerEsp32CamBlink() {
    }

    /** Blinks the status LED five times per second. */
    public static void main(String[] args) {
        DigitalOutput led = DigitalOutput.of(MCU.getGpio().getBuiltinLed());
        while (true) {
            led.high();
            Delay.millis(BLINK_DELAY_MILLIS);
            led.low();
            Delay.millis(BLINK_DELAY_MILLIS);
        }
    }
}
