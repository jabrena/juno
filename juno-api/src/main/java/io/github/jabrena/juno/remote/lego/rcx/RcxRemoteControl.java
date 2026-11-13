package io.github.jabrena.juno.remote.lego.rcx;

import io.github.jabrena.juno.hardware.MCU;
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
 * with a 16-bit button mask. Call {@link #start()} when the main loop also does work that must not be delayed by
 * infrared decoding; the virtual receiver thread then puts button transitions into a fixed-size queue.
 *
 * <p>The last non-zero button mask is retained for 300 milliseconds when a repeat frame is missed, then reported as
 * released. A release frame from the handset reports zero immediately. A frame whose opcode toggle bit differs from the
 * previous frame's is a new transmission, so pressing the same button again queues it again. Calls to
 * {@link #buttons()} block briefly while a frame is decoded; {@link #start()} moves those calls to a virtual receiver
 * thread, which waits inside the native byte reader so it sees the start bit of each frame's first byte.
 */
public final class RcxRemoteControl {
    private static final int BAUD = 2400;
    private static final int OPCODE_REMOTE = 0xD2;
    private static final int OPCODE_TOGGLE = 0x08;
    private static final int GAP_MICROS = 3000;
    private static final int FIRST_BYTE_MICROS = 1000;
    private static final int LISTEN_MICROS = 5000;
    private static final int HOLD_MILLIS = 300;
    private static final int FRAME_VALUES = 4;
    private static final int EVENT_QUEUE_CAPACITY = 16;

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
    private final int[] buttonEvents = new int[EVENT_QUEUE_CAPACITY];
    private int buttonState;
    private int buttonStateAt;
    private int lastToggle = -1;
    private int eventHead;
    private int eventTail;
    private int eventCount;
    private int droppedEventCount;
    private boolean started;
    private volatile boolean listening;

    /** A receive-only remote control whose timer is the board's own clock ({@link MCU#getClock()}). */
    public RcxRemoteControl(InfraredReceiver receiver) {
        this(receiver, MCU.getClock());
    }

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
        if (listening) {
            synchronized (this) {
                return currentButtonState();
            }
        }
        synchronized (this) {
            if (Infrared.receiving() && readFrame(FIRST_BYTE_MICROS)) {
                acceptFrame();
            }
            return currentButtonState();
        }
    }

    /** Starts a virtual receiver thread that decodes frames and queues button transitions. */
    public void start() {
        synchronized (this) {
            if (started) {
                return;
            }
            started = true;
            listening = true;
        }
        Thread.startVirtualThread(this::receiveLoop);
    }

    /** Stops the virtual receiver thread after its current decode pass. */
    public void stop() {
        listening = false;
    }

    /** Returns the next queued raw button mask, or zero when the queue is empty. */
    public int pollButtonMask() {
        synchronized (this) {
            if (eventCount == 0) {
                return 0;
            }
            int mask = buttonEvents[eventHead];
            eventHead = (eventHead + 1) % EVENT_QUEUE_CAPACITY;
            eventCount--;
            return mask;
        }
    }

    /** Returns the next queued button, or {@link RcxRemoteControlButton#NONE} when the queue is empty. */
    public RcxRemoteControlButton pollButton() {
        return RcxRemoteControlButton.fromMask(pollButtonMask());
    }

    /** Returns how many button transitions were discarded because the fixed event queue was full. */
    public int droppedButtonCount() {
        synchronized (this) {
            return droppedEventCount;
        }
    }

    private void receiveLoop() {
        // No receiving() gate and no sleep: a frame that starts while this thread is elsewhere is decoded from a data
        // bit of its 0x55 header and lost. The native wait is not preempted, and the main thread still runs when it
        // returns.
        while (listening) {
            boolean received = readFrame(LISTEN_MICROS);
            synchronized (this) {
                if (received) {
                    acceptFrame();
                }
                currentButtonState();
            }
        }
    }

    private int currentButtonState() {
        if (buttonState != 0 && clock.millis() - buttonStateAt > HOLD_MILLIS) {
            buttonState = 0;
        }
        return buttonState;
    }

    private void acceptFrame() {
        updateButtonState((frame[1] << 8) | frame[2], frame[0] & OPCODE_TOGGLE);
        buttonStateAt = clock.millis();
    }

    private void updateButtonState(int nextState, int toggle) {
        boolean newTransmission = toggle != lastToggle;
        if (nextState != 0 && (nextState != buttonState || newTransmission)) {
            enqueueButton(nextState);
        }
        buttonState = nextState;
        lastToggle = toggle;
    }

    private void enqueueButton(int mask) {
        if (eventCount == EVENT_QUEUE_CAPACITY) {
            droppedEventCount++;
            return;
        }
        buttonEvents[eventTail] = mask;
        eventTail = (eventTail + 1) % EVENT_QUEUE_CAPACITY;
        eventCount++;
    }

    /** Reads and validates one RCX remote-control frame into the fixed frame buffer. */
    private boolean readFrame(int firstByteMicros) {
        int[] data = frame;
        if (Infrared.readByte(firstByteMicros) != 0x55 || Infrared.readByte(GAP_MICROS) != 0xFF
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
