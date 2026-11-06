package io.github.jabrena.juno.remote.lego.pf;

import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.device.ir.InfraredDriver;
import io.github.jabrena.juno.hardware.device.ir.InfraredEmitter;
import io.github.jabrena.juno.util.Delay;

/**
 * The Power Functions infrared link: the message framing and the transmission of a Power Functions command over an
 * {@link InfraredEmitter}, as {@link io.github.jabrena.juno.remote.lego.rcx.RcxLink} is for the RCX. Create one at
 * startup with the emitter and share it with every {@link RemotePF} that talks to the same receivers, so they share
 * one toggle bit.
 *
 * <p>A message is 16 bits of four 4-bit fields (toggle, escape and channel; output and mode; data; checksum) sent as a
 * 38 kHz burst of 6 cycles per bit, followed by a pause of 10 cycles for a 0, 21 for a 1 and 39 for the start and stop
 * marks. It is sent six times, so the receiver does not miss it, which takes about 80 ms.
 */
public final class PFLink {
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

    private final InfraredEmitter emitter;
    private final InfraredDriver driver;
    private int toggleBit;

    /** A link that transmits through {@code emitter}, for example an {@code HX53InfraredEmitter} or a {@code GroveInfraredEmitter}. */
    public PFLink(InfraredEmitter emitter) {
        this.emitter = emitter;
        driver = new InfraredDriver(emitter.driver(), -1, emitter.signalPin(), 0);
    }

    /** Transmits one single-output message: {@code output} of the receiver on {@code channel} set to {@code pwm}. */
    void sendSingle(int channel, int output, int pwm) {
        transmit(toggleBit | (channel & 0x3), MODE_SINGLE_OUTPUT | (output & 0x1), pwm);
        toggleBit = toggleBit ^ TOGGLE;
    }

    /** Transmits one combined message, with the blue output's and the red output's PWM nibbles, to {@code channel}. */
    void sendCombined(int channel, int bluePwm, int redPwm) {
        transmit(ESCAPE | (channel & 0x3), bluePwm, redPwm);
    }

    private void transmit(int nibble1, int nibble2, int nibble3) {
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
