package io.github.jabrena.juno.api.motors;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;

/**
 * Sweeps a 3-wire hobby servo back and forth between 0 and 180 degrees, backed by the Arduino
 * {@code Servo} library.
 *
 * <h2>Wiring</h2>
 *
 * <pre>{@code
 * Servo RED    -> Arduino 5V (small servos only; a separate 5-6V supply, with its GND tied to
 *                 Arduino GND, avoids brownouts for larger servos under load)
 * Servo BLACK  -> Arduino GND
 * Servo YELLOW -> Arduino D10 (signal)
 * }</pre>
 *
 * <p>This example targets a 270 degree servo, but {@link Servo#write(int)} only commands the
 * Arduino {@code Servo} library's standard 0-180 degree range — the remaining 90 degrees of
 * physical travel are unreachable through this API.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class ServoSweep {
    private static final int SERVO_PIN = 10;
    private static final int MIN_ANGLE = 0;
    private static final int MAX_ANGLE = 180;
    private static final int STEP_DEGREES = 1;
    private static final int STEP_DELAY_MILLIS = 15;

    public static void main(String[] args) {

        Servo servo = Servo.of(SERVO_PIN);

        while (true) {
            for (int angle = MIN_ANGLE; angle <= MAX_ANGLE; angle += STEP_DEGREES) {
                servo.write(angle);
                Delay.millis(STEP_DELAY_MILLIS);
            }

            for (int angle = MAX_ANGLE; angle >= MIN_ANGLE; angle -= STEP_DEGREES) {
                servo.write(angle);
                Delay.millis(STEP_DELAY_MILLIS);
            }
        }
    }
}
