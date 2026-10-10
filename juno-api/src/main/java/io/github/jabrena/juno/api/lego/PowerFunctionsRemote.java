package io.github.jabrena.juno.api.lego;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.ir.Infrared;

/**
 * A LEGO Power Functions infrared remote (8885), which drives the motors, lights and servos plugged
 * into an IR Receiver (8884) set to one of its four channels. It is plain Java on top of the raw
 * pulses of {@link Infrared}: see there for the wiring of the IR LED (with a series resistor) on the
 * transmit pin, pointed at the receiver.
 *
 * <p>Every command is a 16-bit message of 4-bit fields (toggle, escape and channel; output and mode;
 * data; and a checksum) sent as a 38 kHz burst of 6 cycles per bit followed by a pause of 10 cycles
 * for a 0, 21 for a 1 and 39 for the start and stop marks, the way the LEGO Power Functions RC
 * protocol and the common Arduino libraries do. Each command is sent six times so the receiver does
 * not miss it, which takes about 80 ms; a motor keeps its speed until the next command or about
 * a second without one, so repeat the command in a loop to keep it running.
 *
 * <p>Each receiver has two outputs, {@link #OUTPUT_RED} and {@link #OUTPUT_BLUE}, and the receiver's
 * channel switch (1 to 4) must match the {@code channel} argument. Sending is blocking. Works on
 * both boards. The pulse timings follow the published protocol and have not been verified on a
 * receiver.
 */
public final class PowerFunctionsRemote {
    private PowerFunctionsRemote() {
    }

    public static final int CHANNEL_1 = 0;
    public static final int CHANNEL_2 = 1;
    public static final int CHANNEL_3 = 2;
    public static final int CHANNEL_4 = 3;

    /** The receiver's two outputs; the red one is also called A, the blue one B. */
    public static final int OUTPUT_RED = 0;
    public static final int OUTPUT_BLUE = 1;

    /** The fastest speed in either direction for {@link #setSpeed}; the PWM has seven steps. */
    public static final int MAX_SPEED = 7;

    private static final int CARRIER_CYCLE_MICROS = 26;
    private static final int MARK_MICROS = 6 * CARRIER_CYCLE_MICROS;
    private static final int PAUSE_START_STOP_MICROS = 39 * CARRIER_CYCLE_MICROS;
    private static final int PAUSE_ONE_MICROS = 21 * CARRIER_CYCLE_MICROS;
    private static final int PAUSE_ZERO_MICROS = 10 * CARRIER_CYCLE_MICROS;
    private static final int REPEATS = 6;
    private static final int GAP_UNIT_MICROS = 77;
    private static final int MODE_SINGLE_OUTPUT = 0x4;
    private static final int ESCAPE = 0x4;
    private static final int TOGGLE = 0x8;
    private static final int PWM_FLOAT = 0x0;
    private static final int PWM_BRAKE = 0x8;

    private static int toggle;

    /** Configures {@code transmitPin} for the IR LED. */
    public static void begin(int transmitPin) {
        Infrared.begin(-1, transmitPin, 0);
    }

    /**
     * Runs {@code output} of the receiver on {@code channel} at {@code speed}, {@code -7..7}: positive
     * for one direction, negative for the other, {@code 0} lets it coast. Out-of-range values are clamped.
     */
    public static void setSpeed(int channel, int output, int speed) {
        sendSingle(channel, output, pwm(speed));
    }

    /** Actively brakes {@code output}, unlike {@code setSpeed(channel, output, 0)}, which lets it coast. */
    public static void brake(int channel, int output) {
        sendSingle(channel, output, PWM_BRAKE);
    }

    /**
     * Sets both outputs of the receiver on {@code channel} in one message, each {@code -7..7} as in
     * {@link #setSpeed}. It is a single command, so use it to start two motors together.
     */
    public static void setSpeeds(int channel, int redSpeed, int blueSpeed) {
        send(ESCAPE | (channel & 0x3), pwm(blueSpeed), pwm(redSpeed));
    }

    /** Lets both outputs of the receiver on {@code channel} coast. */
    public static void stop(int channel) {
        send(ESCAPE | (channel & 0x3), PWM_FLOAT, PWM_FLOAT);
    }

    private static void sendSingle(int channel, int output, int pwm) {
        send(toggle | (channel & 0x3), MODE_SINGLE_OUTPUT | (output & 0x1), pwm);
        toggle = toggle ^ TOGGLE;
    }

    /** Maps a speed to the protocol's PWM nibble: 1..7 forward, 15..9 backward, 0 float. */
    private static int pwm(int speed) {
        int limited = speed > MAX_SPEED ? MAX_SPEED : speed < -MAX_SPEED ? -MAX_SPEED : speed;
        return limited >= 0 ? limited : 16 + limited;
    }

    private static void send(int nibble1, int nibble2, int nibble3) {
        int checksum = 0xF ^ nibble1 ^ nibble2 ^ nibble3;
        int message = (nibble1 << 12) | (nibble2 << 8) | (nibble3 << 4) | checksum;
        for (int repeat = 0; repeat < REPEATS; repeat++) {
            Delay.micros(gap(repeat, nibble1 & 0x3));
            sendMark(PAUSE_START_STOP_MICROS);
            for (int bit = 15; bit >= 0; bit--) {
                sendMark(((message >> bit) & 1) != 0 ? PAUSE_ONE_MICROS : PAUSE_ZERO_MICROS);
            }
            sendMark(PAUSE_START_STOP_MICROS);
        }
    }

    /** The silence before each repeat; later repeats wait longer on higher channels. */
    private static int gap(int repeat, int channel) {
        int units = repeat == 0 ? 3 - channel : repeat < 3 ? 5 : 5 + (channel + 1) * 2;
        return units * GAP_UNIT_MICROS;
    }

    private static void sendMark(int pauseMicros) {
        Infrared.mark(MARK_MICROS);
        Infrared.space(pauseMicros);
    }
}
