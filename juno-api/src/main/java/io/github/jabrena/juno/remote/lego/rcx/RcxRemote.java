package io.github.jabrena.juno.remote.lego.rcx;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.time.Clock;

/**
 * The LEGO Mindstorms RCX infrared protocol, recognized as compiler intrinsics by Juno: receives
 * the button state of an RCX Remote Control (9709) and transmits the same remote-control and
 * message commands to an RCX brick. It is plain Java on top of {@link Infrared}, which carries the
 * 2400 baud bytes; see there for the wiring of the IR receiver module and the IR LED. For the Scout
 * brick, see {@link ScoutRemote}.
 *
 * <p>Every message is {@code 55 FF 00} followed by each byte and its complement: the opcode,
 * its arguments and a checksum. The remote sends opcode {@code 0xD2} with a 16-bit
 * button mask, repeatedly while a button is held and once more with the mask {@code 0} on release.
 *
 * <p>The mask goes on the wire high byte first ({@code D2 hh ll}); the button constants below use the decoded 16-bit
 * mask order.
 *
 * <p>Receiving and sending are blocking: a frame takes about 50 ms at 2400 baud, during which
 * {@link #buttons()} (or {@link #message()}) decodes it. Call them regularly from the main loop,
 * not while {@code Delay.millis} is waiting. Works on both boards.
 */
public final class RcxRemote {
    private final RcxLink link;
    private final Clock clock;

    /** A remote on the board's own infrared link and timer. Create it once, at startup. */
    public RcxRemote() {
        this(new RcxLink(), MCU.getClock());
    }

    /** A remote on a link shared with other RCX classes, so they share one toggle bit. */
    public RcxRemote(RcxLink link) {
        this(link, MCU.getClock());
    }

    /** A remote on {@code link}, timing its button hold on {@code clock}: a simulated clock in a unit test. */
    public RcxRemote(RcxLink link, Clock clock) {
        this.link = link;
        this.clock = clock;
    }


    private static final int HOLD_MILLIS = 300;

    private int buttonState;
    private int buttonStateAt;
    private int lastMessage = -1;

    /** {@link #buttons} bits: the three message buttons at the top of the remote. */
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

    /**
     * Configures the pins: {@code receivePin} for an IR receiver module and {@code transmitPin} for
     * an IR LED, either {@code -1} when unused.
     */
    public void begin(int receivePin, int transmitPin) {
        link.begin(receivePin, transmitPin);
    }

    /**
     * The buttons currently held on the remote as a mask of the button constants, or {@code 0} when
     * none is held. Decodes a frame if one is arriving; the last state is kept for 300 ms and then
     * counts as released, in case the release message was missed.
     */
    public int buttons() {
        poll();
        return buttonState;
    }

    /**
     * The value of the most recent RCX {@code message} command (opcode {@code 0xF7}, e.g. sent by
     * another RCX or by {@link #sendMessage}), {@code 0..255}, or {@code -1} if none has arrived
     * since the last call.
     */
    public int message() {
        poll();
        int value = lastMessage;
        lastMessage = -1;
        return value;
    }

    /** Transmits a remote-control command with {@code buttonMask} to RCX bricks in range, as the remote does. */
    public void sendButtons(int buttonMask) {
        link.send(RcxLink.OPCODE_REMOTE, (buttonMask >> 8) & 0xFF, buttonMask & 0xFF, 2);
    }

    /** Transmits an RCX {@code message} command carrying the low 8 bits of {@code value}. */
    public void sendMessage(int value) {
        link.send(RcxLink.OPCODE_MESSAGE, value & 0xFF, 0, 1);
    }

    private void poll() {
        int arguments = link.receive();
        if (arguments == 2 && link.opcode == RcxLink.OPCODE_REMOTE) {
            buttonState = (link.argument0 << 8) | link.argument1;
            buttonStateAt = clock.millis();
        } else if (arguments == 1 && link.opcode == RcxLink.OPCODE_MESSAGE) {
            lastMessage = link.argument0;
        }
        if (buttonState != 0 && clock.millis() - buttonStateAt > HOLD_MILLIS) {
            buttonState = 0;
        }
    }
}
