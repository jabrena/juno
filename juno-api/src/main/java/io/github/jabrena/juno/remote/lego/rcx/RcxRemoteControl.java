package io.github.jabrena.juno.remote.lego.rcx;

import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.device.ir.InfraredDriver;
import io.github.jabrena.juno.hardware.device.ir.InfraredReceiver;
import io.github.jabrena.juno.hardware.time.Clock;

/**
 * A receive-only LEGO Mindstorms RCX remote control, such as the 9738 handset.
 *
 * <p>Create it once at startup with the HX-M121 or Grove infrared receiver that hears the handset. The object configures
 * the receiver at the RCX remote's 2400 baud and allocates its four-byte frame buffer during construction; polling with
 * {@link #buttons()} is allocation-free. The receiver must be pointed at the handset, which sends opcode {@code 0xD2}
 * with a 16-bit button mask.
 *
 * <p>The last non-zero button mask is retained for 300 milliseconds when a repeat frame is missed, then reported as
 * released. A release frame from the handset reports zero immediately. Calls block briefly while a frame is decoded, so
 * call {@link #buttons()} regularly from the main loop.
 */
public final class RcxRemoteControl {
    private static final int BAUD = 2400;
    private static final int OPCODE_REMOTE = 0xD2;
    private static final int OPCODE_TOGGLE = 0x08;
    private static final int GAP_MICROS = 3000;
    private static final int FIRST_BYTE_MICROS = 1000;
    private static final int HOLD_MILLIS = 300;
    private static final int FRAME_VALUES = 4;

    /** The three message buttons at the top of the handset. */
    public static final int MESSAGE_1 = 0x0001;
    public static final int MESSAGE_2 = 0x0002;
    public static final int MESSAGE_3 = 0x0004;
    /** Motor buttons: forward is the up arrow, backward the down arrow. */
    public static final int A_FORWARD = 0x0008;
    public static final int B_FORWARD = 0x0010;
    public static final int C_FORWARD = 0x0020;
    public static final int A_BACKWARD = 0x0040;
    public static final int B_BACKWARD = 0x0080;
    public static final int C_BACKWARD = 0x0100;
    /** The five program-select buttons. */
    public static final int PROGRAM_1 = 0x0200;
    public static final int PROGRAM_2 = 0x0400;
    public static final int PROGRAM_3 = 0x0800;
    public static final int PROGRAM_4 = 0x1000;
    public static final int PROGRAM_5 = 0x2000;
    /** The stop button, and the sound button. */
    public static final int STOP = 0x4000;
    public static final int SOUND = 0x8000;

    private final Clock clock;
    private final InfraredDriver driver;
    private final int[] frame = new int[FRAME_VALUES];
    private int buttonState;
    private int buttonStateAt;

    /** Configures a receive-only RCX infrared link from {@code receiver}. */
    public RcxRemoteControl(InfraredReceiver receiver, Clock clock) {
        if (receiver.signalPin() < 0) {
            throw new IllegalArgumentException("RCX remote control needs an infrared receiver signal pin");
        }
        this.clock = clock;
        driver = new InfraredDriver(receiver.driver(), receiver.signalPin(), -1, BAUD);
    }

    /**
     * Returns the handset's currently held buttons as a mask, or zero when no button is held.
     *
     * <p>The call is allocation-free after construction.
     */
    public int buttons() {
        synchronized (this) {
            if (Infrared.receiving() && readFrame()) {
                buttonState = (frame[1] << 8) | frame[2];
                buttonStateAt = clock.millis();
            }
            if (buttonState != 0 && clock.millis() - buttonStateAt > HOLD_MILLIS) {
                buttonState = 0;
            }
            return buttonState;
        }
    }

    /** Reads and validates one RCX remote-control frame into the fixed frame buffer. */
    private boolean readFrame() {
        int[] data = frame;
        if (Infrared.readByte(FIRST_BYTE_MICROS) != 0x55 || Infrared.readByte(GAP_MICROS) != 0xFF
                || Infrared.readByte(GAP_MICROS) != 0x00) {
            return false;
        }
        for (int index = 0; index < FRAME_VALUES; index++) {
            int value = Infrared.readByte(GAP_MICROS);
            int complement = Infrared.readByte(GAP_MICROS);
            if (value < 0 || complement < 0 || (value ^ complement) != 0xFF) {
                return false;
            }
            data[index] = value;
        }
        if ((data[0] & ~OPCODE_TOGGLE) != OPCODE_REMOTE) {
            return false;
        }
        int checksum = data[0] + data[1] + data[2];
        return (checksum & 0xFF) == data[3];
    }
}
