package io.github.jabrena.juno.api.thirdparty.servo;

import io.github.jabrena.juno.api.util.Delay;

/**
 * Sweeps 3-wire hobby servos back and forth between 0 and 180 degrees from a "16 x 12-bit PWM &amp; Servo
 * Shield" ({@link PwmServoShield}). Every servo on channels 0 to {@code SERVO_COUNT - 1} moves together.
 *
 * <h2>Wiring</h2>
 *
 * <p>Each channel is a column of three pins: {@code GND} on the row nearest the board edge, {@code V+} in the
 * middle and {@code PWM} on the row nearest the chip. Channel 0 is the column next to the green terminal block;
 * a second servo goes on channel 1, and so on.
 *
 * <pre>{@code
 * Servo power  -> green terminal block: 5-6V supply (+) and (-), e.g. 4 x AA; the V+ pin rail is fed only from
 *                 here, so a servo without it does not move
 * VCC select   -> 5V (the UNO's logic level)
 *
 * Servo BROWN  -> channel 0 GND
 * Servo RED    -> channel 0 V+
 * Servo YELLOW -> channel 0 PWM
 * }</pre>
 */
public final class PwmServoShieldDemo {
    private static final int SERVO_COUNT = 1;
    private static final int STEP_DEGREES = 1;
    private static final int STEP_DELAY_MILLIS = 15;

    public static void main(String[] args) {
        PwmServoShield.begin();

        while (true) {
            for (int angle = PwmServoShield.MIN_ANGLE; angle <= PwmServoShield.MAX_ANGLE; angle += STEP_DEGREES) {
                writeAll(angle);
                Delay.millis(STEP_DELAY_MILLIS);
            }
            for (int angle = PwmServoShield.MAX_ANGLE; angle >= PwmServoShield.MIN_ANGLE; angle -= STEP_DEGREES) {
                writeAll(angle);
                Delay.millis(STEP_DELAY_MILLIS);
            }
        }
    }

    private static void writeAll(int angle) {
        for (int channel = 0; channel < SERVO_COUNT; channel++) {
            PwmServoShield.write(channel, angle);
        }
    }
}
