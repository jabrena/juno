package io.github.jabrena.juno.remote.lego.rcx;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.ir.HX53InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.HXM121InfraredReceiver;
import io.github.jabrena.juno.hardware.device.ir.Infrared;
import io.github.jabrena.juno.hardware.device.ir.InfraredEmitter;
import io.github.jabrena.juno.hardware.device.ir.InfraredReceiver;
import io.github.jabrena.juno.hardware.time.Clock;

/**
 * A LEGO Mindstorms RCX brick reached over infrared, in the manner of LeJOS's {@code RemoteNXT}: create it once, at
 * startup, with the infrared {@link RcxLink} and the pins of the IR receiver module and the IR LED, call
 * {@link #begin}, and get the brick's parts from it — its motors ({@link #getMotorA()}, {@link #getMotorB()}, {@link #getMotorC()}), its sensor inputs
 * ({@link #getSensor1()}, {@link #getSensor2()}, {@link #getSensor3()}), its display ({@link #getLcd()}), its speaker
 * ({@link #getAudio()}), its batteries ({@link #getBattery()}) and its firmware loader ({@link #getFirmware()}). Every
 * part shares one {@link RcxLink}, so the brick sees one toggle bit, and is allocated here, so getting one allocates
 * nothing. The display commands need Juno's own RCX firmware ({@code RcxRemoteFirmware}); the rest also works with
 * LEGO's.
 *
 * <p>It also speaks as the RCX Remote Control (9709) does: {@link #buttons()} decodes the remote's button state and
 * {@link #sendButtons} transmits it, so the brick in remote-control mode drives its motors with no firmware commands
 * at all, and {@link #message()} and {@link #sendMessage} carry RCX {@code message} values. It is plain Java on top of
 * {@link Infrared}, which carries the 2400 baud bytes; see there for the wiring.
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
public final class RemoteRcx {
    private final RcxLink link;
    private final int channel;
    private final InfraredReceiver receiver;
    private final InfraredEmitter emitter;
    private final Clock clock;
    private final RcxMotor motorA;
    private final RcxMotor motorB;
    private final RcxMotor motorC;
    private final RcxSensor sensor1;
    private final RcxSensor sensor2;
    private final RcxSensor sensor3;
    private final RcxLcd lcd;
    private final RcxAudio audio;
    private final RcxBattery battery;
    private final RcxButtons buttons;
    private final RcxFirmware firmware;

    /**
     * A brick reached over {@code link}, with an IR receiver module on {@code receivePin} and an IR LED on
     * {@code transmitPin}, either {@code -1} when unused. Create it once, at startup, and call {@link #begin} to set up
     * the pins. Share {@code link} with any other RCX class, so they share one toggle bit.
     */
    public RemoteRcx(RcxLink link, int receivePin, int transmitPin) {
        this(link, new HXM121InfraredReceiver(receivePin), new HX53InfraredEmitter(transmitPin), ArduinoUno.getClock());
    }

    /** A brick reached through typed infrared modules, using the UNO clock. */
    public RemoteRcx(RcxLink link, InfraredReceiver receiver, InfraredEmitter emitter) {
        this(link, 0, receiver, emitter, ArduinoUno.getClock());
    }

    /** A brick on Juno firmware channel {@code 1..5}, reached through typed infrared modules. */
    public RemoteRcx(RcxLink link, int channel, InfraredReceiver receiver, InfraredEmitter emitter) {
        this(link, channel, receiver, emitter, ArduinoUno.getClock());
    }

    /** A brick reached through typed infrared modules and timed by {@code clock}. */
    public RemoteRcx(RcxLink link, InfraredReceiver receiver, InfraredEmitter emitter, Clock clock) {
        this(link, 0, receiver, emitter, clock);
    }

    /** A brick on Juno firmware channel {@code 1..5}, reached through typed infrared modules and timed by {@code clock}. */
    public RemoteRcx(RcxLink link, int channel, InfraredReceiver receiver, InfraredEmitter emitter, Clock clock) {
        this.link = link;
        this.channel = channel;
        this.receiver = receiver;
        this.emitter = emitter;
        this.clock = clock;
        RcxBrick brick = new RcxBrick(link, channel);
        this.motorA = new RcxMotor(brick, RcxBrick.OUTPUT_A);
        this.motorB = new RcxMotor(brick, RcxBrick.OUTPUT_B);
        this.motorC = new RcxMotor(brick, RcxBrick.OUTPUT_C);
        this.sensor1 = new RcxSensor(brick, RcxBrick.INPUT_1);
        this.sensor2 = new RcxSensor(brick, RcxBrick.INPUT_2);
        this.sensor3 = new RcxSensor(brick, RcxBrick.INPUT_3);
        this.lcd = new RcxLcd(brick);
        this.audio = new RcxAudio(brick);
        this.battery = new RcxBattery(brick);
        this.buttons = new RcxButtons(brick);
        this.firmware = new RcxFirmware(link, clock);
    }

    /** As the three-argument constructor, timing the button hold and firmware waits on {@code clock}: a simulated clock in a unit test. */
    public RemoteRcx(RcxLink link, int receivePin, int transmitPin, Clock clock) {
        this(link, new HXM121InfraredReceiver(receivePin), new HX53InfraredEmitter(transmitPin), clock);
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
     * Configures the IR receiver and IR LED pins given to the constructor. Without the receiver, the parts' commands
     * cannot hear the brick's answers: reads return {@code -1} and acknowledgements {@code false}.
     */
    public void begin() {
        link.begin(receiver, emitter);
    }

    /** Output A. */
    public RcxMotor getMotorA() {
        return motorA;
    }

    /** Output B. */
    public RcxMotor getMotorB() {
        return motorB;
    }

    /** Output C. */
    public RcxMotor getMotorC() {
        return motorC;
    }

    /** Sensor input 1. */
    public RcxSensor getSensor1() {
        return sensor1;
    }

    /** Sensor input 2. */
    public RcxSensor getSensor2() {
        return sensor2;
    }

    /** Sensor input 3. */
    public RcxSensor getSensor3() {
        return sensor3;
    }

    /** The display. */
    public RcxLcd getLcd() {
        return lcd;
    }

    /** The speaker. */
    public RcxAudio getAudio() {
        return audio;
    }

    /** The batteries. */
    public RcxBattery getBattery() {
        return battery;
    }

    /** The brick's own View, Prgm and Run buttons; needs Juno's own RCX firmware. */
    public RcxButtons getButtons() {
        return buttons;
    }

    /** The firmware loader, which the brick's boot ROM answers even with no firmware installed. */
    public RcxFirmware getFirmware() {
        return firmware;
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
        if (channel == 0) {
            link.sendButtons(buttonMask);
        } else {
            link.sendButtonsChannel(channel, buttonMask);
        }
    }

    /** Transmits an RCX {@code message} command carrying the low 8 bits of {@code value}. */
    public void sendMessage(int value) {
        if (channel == 0) {
            link.sendMessage(value);
        } else {
            link.sendMessageChannel(channel, value);
        }
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
