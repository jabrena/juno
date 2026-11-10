package io.github.jabrena.juno.backend.h8300;

import io.github.jabrena.juno.backend.h8300.RcxRom.Argument;

/**
 * The regulated differential drive of {@link RcxRemoteFirmware}: outputs A and C, the left and right wheels, each
 * regulated locally by the firmware from its rotation sensor (inputs 1 and 3), so infrared is a command channel and not
 * a control loop. The commands are 0x61 status, 0x62 stop, 0x63 arm, 0x64 speed, 0x74 acceleration, 0x33 and 0x43
 * the left and right targets, 0x52 start and 0x92 brake lead; the acknowledgements,
 * states and payloads are the ones {@code RcxRegulatedProtocol} expects on the host (OpenSpec change
 * {@code rcx-regulated-differential-drive}).
 *
 * <p>The regulator runs once per fresh analog update, every 3 ms, after the sensors are read. Both sides drive at the
 * configured maximum power toward their targets, and the rotation sensors keep them level: a side one count ahead of the
 * other drops a level, two or more counts ahead brakes until the other catches up. A side brakes and is done once it has
 * covered its whole distance from where the motion started, after slowing a level over the last {@link #SLOW_COUNTS},
 * or earlier, by the distance it will coast: each side times its rotation-sensor steps, and brakes once it will reach
 * its target within the {@link #BRAKE_LEAD}, to the cycle. The lead is the wheel's braking time
 * constant in control cycles, since a braked RCX motor coasts on roughly its speed times that constant; 0 turns the
 * early brake off.
 * This suits the RCX: its rotation sensors count 16 steps a turn, too coarse for a position loop to pace a wheel
 * smoothly, and its motors run at nearly the same speed at any power, so a paced trajectory turned into stop-and-go.
 *
 * <p>The profile's speed and acceleration are still checked and stored, for the protocol; the pace is the power's.
 */
final class RcxRegulator {
    static final int OPCODE_STATUS = 0x61;
    static final int OPCODE_STOP = 0x62;
    static final int OPCODE_ARM = 0x63;
    /**
     * The profile is two commands, not one: the ROM takes an opcode's low three bits as its argument count except for
     * 7, which it reads as one argument (0xF7, the message, is one byte). A seven-argument profile at 0x67 therefore
     * never reached the firmware on the brick; each half has four arguments.
     */
    static final int OPCODE_SPEED = 0x64;
    static final int OPCODE_ACCELERATION = 0x74;
    /**
     * A move is three commands for the same reason: the brick dropped the six-argument start at 0x76, while frames of up
     * to four arguments reach the firmware. Each target is staged on its own, and the start begins both at once.
     */
    static final int OPCODE_TARGET_LEFT = 0x33;
    static final int OPCODE_TARGET_RIGHT = 0x43;
    static final int OPCODE_START = 0x52;
    /** Diagnostic: replies with the free-running control-cycle counter, so the host can measure the cycle rate. */
    static final int OPCODE_CYCLES = 0x71;
    /**
     * Diagnostic: replies with each side's power this cycle (0 when braked) and the cycles each was held braked in the
     * current motion, so a floor run shows whether the level-keeping acts.
     */
    static final int OPCODE_DRIVE = 0x81;
    /**
     * Sets the brake lead, in control cycles (session, lead): a side brakes early by the distance it coasts at its speed.
     * Arming resets it to {@link #DEFAULT_BRAKE_LEAD}; refused while a movement runs.
     */
    static final int OPCODE_BRAKE_LEAD = 0x92;

    static final String STATUS = "reg_status";
    static final String STOP = "reg_stop";
    static final String ARM = "reg_arm";
    static final String SET_SPEED = "reg_speed";
    static final String SET_ACCELERATION = "reg_acceleration";
    static final String TARGET_LEFT = "reg_target_left";
    static final String TARGET_RIGHT = "reg_target_right";
    static final String START = "reg_start";
    static final String CYCLES = "reg_cycles";
    static final String DRIVE = "reg_drive";
    static final String SET_BRAKE_LEAD = "reg_brake_lead";
    /** Subroutine: one control cycle, after a fresh analog update. */
    static final String TICK = "reg_tick";
    /** Subroutine: counts a main-loop pass without a fresh analog update, for the stale check. */
    static final String IDLE_TICK = "reg_idle_tick";
    /** Subroutine: an external manual command took A and C back; latches the override and stops both. */
    static final String OVERRIDE = "reg_override";
    /** Subroutine: the remote's stop button stops a running movement on both outputs. */
    static final String REMOTE_STOP = "reg_remote_stop";
    /** Subroutine: stops both outputs by the braking policy, and records that policy for the legacy outputs. */
    static final String STOP_OUTPUTS = "reg_stop_outputs";

    private static final String SNAPSHOT = "reg_snapshot";
    private static final String ACK_ONE = "reg_ack_one";
    private static final String ACK_THREE = "reg_ack_three";
    private static final String LEFT_SIDE = "reg_side_left";
    private static final String RIGHT_SIDE = "reg_side_right";

    /** Acknowledgements, as {@code RcxRegulatedProtocol} numbers them. */
    static final int ACCEPTED = 0;
    static final int DUPLICATE = 1;
    static final int NOT_ARMED_ACK = 2;
    static final int BUSY = 3;
    static final int INVALID_SESSION = 4;
    static final int INVALID_ARGUMENT = 5;
    static final int OUT_OF_RANGE = 6;
    static final int ID_CONFLICT = 7;
    static final int STALE_COMMAND = 8;

    /** The states, as {@code RcxRegulatedProtocol} maps the firmware's codes. */
    static final int UNARMED = 0;
    static final int READY = 1;
    static final int RUNNING = 2;
    static final int COMPLETE = 3;
    static final int STOPPED = 4;
    static final int STALLED = 5;
    static final int TIMED_OUT = 6;
    static final int FAULT = 7;

    static final int FAULT_LEFT_STALL = 0x01;
    static final int FAULT_RIGHT_STALL = 0x02;
    static final int FAULT_STALL = FAULT_LEFT_STALL | FAULT_RIGHT_STALL;
    static final int FAULT_STALE = 0x0C;
    static final int FAULT_TIMEOUT = 0x10;
    static final int FAULT_EXTERNAL_OVERRIDE = 0x20;

    static final int CONFIG_INVERT_LEFT_MOTOR = 0x01;
    static final int CONFIG_INVERT_RIGHT_MOTOR = 0x02;
    static final int CONFIG_INVERT_LEFT_SENSOR = 0x04;
    static final int CONFIG_INVERT_RIGHT_SENSOR = 0x08;
    static final int CONFIG_BRAKE = 0x10;
    private static final int CONFIG_POWER_MASK = 0xE0;
    private static final int CONFIG_POWER_SHIFT_DIVISOR = 32;

    /** The only mapping v1 defines: motor A with sensor 1 on the left, motor C with sensor 3 on the right. */
    static final int MAPPING = 1;
    static final int PROTOCOL_VERSION = 1;
    /** The outputs A and C within the output selection of the legacy commands: bits 0 and 2. */
    static final int REGULATED_OUTPUTS = 0x05;

    /**
     * Control cycles per second: one per fresh analog update, every 3 ms. Measured on the brick with
     * {@link #OPCODE_CYCLES}: 1135 cycles in 3401 ms (task 1.3).
     */
    static final int TICKS_PER_SECOND = 333;
    /** Cycles a driven wheel may stay still before it counts as stalled: one second at {@link #TICKS_PER_SECOND}. */
    static final int STALL_TICKS = TICKS_PER_SECOND;
    /** Over the last this many counts of its distance, a side drives one level below the maximum, to limit overshoot. */
    static final int SLOW_COUNTS = 2;
    /**
     * The brake lead an arm starts with, in control cycles, as {@code RcxRegulatedPair.DEFAULT_BRAKE_LEAD} sends it: on
     * the reference build at power 1 on batteries, 500 mm moves stopped on 31 and 32 of 31 counts.
     */
    static final int DEFAULT_BRAKE_LEAD = 16;
    /** Cycles per step above which a side is too slow to coast: the lead's division needs a byte divisor. */
    private static final int MAX_TIMED_INTERVAL = 0xFF;
    /** The longest timeout whose cycle count, seconds times {@link #TICKS_PER_SECOND}, fits a word. */
    static final int MAX_TIMEOUT_SECONDS = 0xFFFF / TICKS_PER_SECOND;
    /** Placeholder: main-loop passes without a fresh analog update before the sensors count as stale. */
    static final int STALE_PASSES = 200;
    /**
     * Profile speed and acceleration arrive in counts per second, at most this: the conversion divides the rate by 4 into
     * a byte. Far beyond an RCX motor, which turns about 4 times a second, 64 counts.
     */
    static final int MAX_RATE = 255 * 4;

    /** Motor-mode and sensor-type values, as the ROM takes them: the sensor's rotation type and its mode. */
    private static final int SENSOR_TYPE_ROTATION = 4;
    private static final int ROTATION_MODE = 0xE0;
    private static final int STOP_MODE_OFF = 0x40;
    private static final int NO_POWER = 0;
    private static final int FULL_POWER = RcxRom.MOTOR_FULL_POWER;

    /** The regulator's RAM, past the remote's and the display's state. */
    static final int BASE = 0xC120;
    static final int STATE = BASE;
    static final int SESSION = BASE + 1;
    static final int MOTION = BASE + 2;
    static final int FAULTS = BASE + 3;
    static final int CONFIG = BASE + 4;
    static final int MAX_POWER = BASE + 5;
    static final int TOLERANCE = BASE + 6;
    static final int TIMEOUT_SECONDS = BASE + 7;
    static final int SPEED = BASE + 8;
    static final int ACCELERATION = BASE + 10;
    /** Speed and acceleration per cycle, in 1/256 counts (Q8): see {@link #storeRate}. */
    static final int SPEED_TICK = BASE + 12;
    static final int ACCELERATION_TICK = BASE + 14;
    static final int ELAPSED = BASE + 16;
    static final int TIMEOUT_TICKS = BASE + 18;
    static final int IDLE = BASE + 20;
    /** The targets the next start applies, as staged by the target commands. */
    static final int PENDING_LEFT = BASE + 22;
    static final int PENDING_RIGHT = BASE + 24;
    /** Control cycles run since boot, in any state, wrapping at 16 bits: read by {@link #OPCODE_CYCLES}. */
    static final int CYCLES_RUN = BASE + 26;
    /** The brake lead, in control cycles (a byte): see {@link #OPCODE_BRAKE_LEAD}. */
    static final int BRAKE_LEAD = BASE + 28;

    /** Per side: done, power, direction, stall count, target, trajectory, velocity, position and last position. */
    static final int DONE = 0;
    static final int POWER = 1;
    static final int DIRECTION = 2;
    static final int STALL = 4;
    static final int TARGET = 6;
    static final int TRAJECTORY = 8;
    /** The trajectory's velocity, in 1/256 counts per cycle (Q8), so a slow speed is not rounded up to a count. */
    static final int VELOCITY = 10;
    static final int POSITION = 12;
    static final int LAST = 14;
    /** The trajectory's fraction of a count, in 1/256 counts, carried from one cycle's step to the next. */
    static final int FRACTION = 16;
    /** The side's position when the motion started, and the distance to cover from it, in counts (unsigned). */
    static final int START_POSITION = 18;
    static final int DISTANCE = 20;
    /** Cycles of the current motion the side was held braked to let the other catch up: a diagnostic. */
    static final int HELD_CYCLES = 22;
    /**
     * Control cycles between the side's last two rotation-sensor steps, its speed's inverse: {@code 0xFFFF} until it has
     * stepped twice in the motion, so the first step, taken from rest, is not read as its speed.
     */
    static final int STEP_INTERVAL = 24;
    /** Control cycles since the side's last rotation-sensor step, or since the motion started. */
    static final int SINCE_STEP = 26;
    /** Whether the side has stepped yet in the motion (a byte, 0 or 1): the interval is timed from the second step. */
    static final int STEPS_TAKEN = 28;

    /** The sensor struct's converted value: {@code char type, char mode, short raw, short value, char boolean}. */
    private static final int SENSOR_SIZE = 8;
    private static final int SENSOR_VALUE = 4;

    /** One regulated side: its RAM, sensor struct, motor output and the configuration bits that flip it. */
    private record Side(int base, int sensor, int motor, int modes, int motorInvert, int sensorInvert, int stallFault) {
    }

    private static final Side LEFT = new Side(BASE + 0x20, RcxSensorCommands.SENSORS, RcxRom.MOTOR_A,
            RcxMotorCommands.MODES, CONFIG_INVERT_LEFT_MOTOR, CONFIG_INVERT_LEFT_SENSOR, FAULT_LEFT_STALL);
    private static final Side RIGHT = new Side(BASE + 0x40, RcxSensorCommands.SENSORS + 2 * SENSOR_SIZE,
            RcxRom.MOTOR_C, RcxMotorCommands.MODES + 4, CONFIG_INVERT_RIGHT_MOTOR, CONFIG_INVERT_RIGHT_SENSOR,
            FAULT_RIGHT_STALL);
    private static final Side[] SIDES = {LEFT, RIGHT};


    private final RcxCode code;
    private final H8Assembler assembler;

    RcxRegulator(RcxCode code) {
        this.code = code;
        this.assembler = code.assembler();
    }

    /** Branches to {@code target} while the regulator owns A and C: armed and ready, or running. Clobbers r0l. */
    void branchIfOwned(String target) {
        assembler.loadByte(STATE, H8ByteRegister.R0L);
        assembler.compareImmediate(READY, H8ByteRegister.R0L);
        code.jumpIf(H8Condition.EQUAL, target);
        assembler.compareImmediate(RUNNING, H8ByteRegister.R0L);
        code.jumpIf(H8Condition.EQUAL, target);
    }

    /** Branches to {@code target} unless the regulator owns A and C. Clobbers r0l. */
    void branchIfNotOwned(String target) {
        String owned = code.newLabel();
        branchIfOwned(owned);
        assembler.jump(target);
        assembler.label(owned);
    }

    /** The command handlers, dispatched from {@link RcxRemoteFirmware}. */
    void handlers() {
        status();
        stop();
        arm();
        speed();
        acceleration();
        target(TARGET_LEFT, PENDING_LEFT);
        target(TARGET_RIGHT, PENDING_RIGHT);
        start();
        cycles();
        drive();
        brakeLead();
    }

    /** The subroutines the handlers and the main loop call. */
    void subroutines() {
        snapshot();
        side(LEFT, LEFT_SIDE);
        side(RIGHT, RIGHT_SIDE);
        tick();
        idleTick();
        stopOutputs();
        override();
        remoteStop();
        sharedAcknowledgements();
    }

    /**
     * Replies with the live positions: the control cycle refreshes them only while a movement runs, so without this
     * snapshot a status read after arming reported the previous run's last positions, and the host's next targets were
     * offset by them (the reference build overshot and timed out).
     */
    private void status() {
        assembler.label(STATUS);
        assembler.call(SNAPSHOT);
        replyByte(SESSION, 1);
        replyByte(MOTION, 2);
        replyByte(STATE, 3);
        replyByte(FAULTS, 4);
        replyWord(LEFT.base() + POSITION, 5);
        replyWord(RIGHT.base() + POSITION, 7);
        code.replyWith(9);
    }

    private void stop() {
        assembler.label(STOP);
        requireSession(3);
        requireMotion(STALE_COMMAND, 3);
        String running = code.newLabel();
        assembler.loadByte(STATE, H8ByteRegister.R0L);
        assembler.compareImmediate(RUNNING, H8ByteRegister.R0L);
        assembler.branch(H8Condition.EQUAL, running);
        replyAck(DUPLICATE, 3);
        assembler.label(running);
        assembler.moveImmediate(STOPPED, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, STATE);
        assembler.call(STOP_OUTPUTS);
        replyAck(ACCEPTED, 3);
    }

    private void arm() {
        assembler.label(ARM);
        String mapped = code.newLabel();
        loadByteWord(RcxCode.REQUEST + 2, H8Register.R0);
        assembler.moveImmediate(MAPPING, H8Register.R1);
        assembler.compare(H8Register.R1, H8Register.R0);
        assembler.branch(H8Condition.EQUAL, mapped);
        replyArm(INVALID_ARGUMENT);
        assembler.label(mapped);
        String idle = code.newLabel();
        assembler.loadByte(STATE, H8ByteRegister.R0L);
        assembler.compareImmediate(RUNNING, H8ByteRegister.R0L);
        assembler.branch(H8Condition.NOT_EQUAL, idle);
        replyArm(BUSY);
        assembler.label(idle);
        assembler.loadByte(RcxCode.REQUEST + 1, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, SESSION);
        assembler.loadByte(RcxCode.REQUEST + 3, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, CONFIG);
        // The maximum motor power is configuration bits 5-7, divided down to 0..7.
        assembler.loadByte(RcxCode.REQUEST + 3, H8ByteRegister.R0L);
        assembler.and(CONFIG_POWER_MASK, H8ByteRegister.R0L);
        assembler.moveImmediate(0, H8ByteRegister.R0H);
        assembler.moveImmediate(CONFIG_POWER_SHIFT_DIVISOR, H8ByteRegister.R1L);
        assembler.divideUnsigned(H8ByteRegister.R1L, H8Register.R0);
        assembler.moveImmediate(0, H8ByteRegister.R0H);
        assembler.storeByte(H8ByteRegister.R0L, MAX_POWER);
        clearHistory();
        assembler.moveImmediate(DEFAULT_BRAKE_LEAD, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, BRAKE_LEAD);
        assembler.call(STOP_OUTPUTS);
        configureRotation(0);
        configureRotation(2);
        assembler.moveImmediate(READY, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, STATE);
        replyArm(ACCEPTED);
    }

    /** Speed (counts per second, low byte first) and the position tolerance: session, speed, tolerance. */
    private void speed() {
        assembler.label(SET_SPEED);
        requireSession(1);
        rejectWhileRunning();
        checkRate(RcxCode.REQUEST + 2);
        assembler.loadByte(RcxCode.REQUEST + 4, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, TOLERANCE);
        storeRate(RcxCode.REQUEST + 2, SPEED, SPEED_TICK);
        replyAck(ACCEPTED, 1);
    }

    /** Acceleration (counts per second squared, low byte first) and the timeout: session, acceleration, timeout. */
    private void acceleration() {
        assembler.label(SET_ACCELERATION);
        requireSession(1);
        rejectWhileRunning();
        checkRate(RcxCode.REQUEST + 2);
        assembler.loadByte(RcxCode.REQUEST + 4, H8ByteRegister.R0L);
        assembler.compareImmediate(0, H8ByteRegister.R0L);
        String timeout = code.newLabel();
        assembler.branch(H8Condition.NOT_EQUAL, timeout);
        replyAck(INVALID_ARGUMENT, 1);
        assembler.label(timeout);
        assembler.compareImmediate(MAX_TIMEOUT_SECONDS, H8ByteRegister.R0L);
        String timeoutFits = code.newLabel();
        assembler.branch(H8Condition.LOWER_OR_SAME, timeoutFits);
        replyAck(OUT_OF_RANGE, 1);
        assembler.label(timeoutFits);
        assembler.storeByte(H8ByteRegister.R0L, TIMEOUT_SECONDS);
        storeRate(RcxCode.REQUEST + 2, ACCELERATION, ACCELERATION_TICK);
        replyAck(ACCEPTED, 1);
    }

    /** A profile change while a movement runs is refused as busy. */
    private void rejectWhileRunning() {
        String notRunning = code.newLabel();
        assembler.loadByte(STATE, H8ByteRegister.R0L);
        assembler.compareImmediate(RUNNING, H8ByteRegister.R0L);
        assembler.branch(H8Condition.NOT_EQUAL, notRunning);
        replyAck(BUSY, 1);
        assembler.label(notRunning);
    }

    /** Replies with the control cycles run since boot, low byte first; takes the session byte, which it ignores. */
    private void cycles() {
        assembler.label(CYCLES);
        assembler.load(CYCLES_RUN, H8Register.R0);
        code.replyWithWord(H8Register.R0);
    }

    /** Replies left power, right power, then each side's held cycles, low byte first; takes the session byte. */
    private void drive() {
        assembler.label(DRIVE);
        replyByte(LEFT.base() + POWER, 1);
        replyByte(RIGHT.base() + POWER, 2);
        replyWord(LEFT.base() + HELD_CYCLES, 3);
        replyWord(RIGHT.base() + HELD_CYCLES, 5);
        code.replyWith(7);
    }

    /** Sets the brake lead (session, lead in control cycles); refused while a movement runs. */
    private void brakeLead() {
        assembler.label(SET_BRAKE_LEAD);
        requireSession(1);
        rejectWhileRunning();
        assembler.loadByte(RcxCode.REQUEST + 2, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, BRAKE_LEAD);
        replyAck(ACCEPTED, 1);
    }

    /** Stages one side's target (session, target low byte first) for the next start; a running movement keeps its own. */
    private void target(String label, int pending) {
        assembler.label(label);
        requireSession(1);
        loadWordArgument(RcxCode.REQUEST + 2, H8Register.R0);
        assembler.store(H8Register.R0, pending);
        replyAck(ACCEPTED, 1);
    }

    private void start() {
        assembler.label(START);
        requireSession(3);
        String motionNonZero = code.newLabel();
        loadByteWord(RcxCode.REQUEST + 2, H8Register.R0);
        assembler.moveImmediate(0, H8Register.R1);
        assembler.compare(H8Register.R1, H8Register.R0);
        assembler.branch(H8Condition.NOT_EQUAL, motionNonZero);
        replyAck(INVALID_ARGUMENT, 3);
        assembler.label(motionNonZero);
        String newer = code.newLabel();
        loadByteWord(RcxCode.REQUEST + 2, H8Register.R0);
        loadByteWord(MOTION, H8Register.R1);
        assembler.compare(H8Register.R1, H8Register.R0);
        code.jumpIf(H8Condition.HIGHER, newer);
        String same = code.newLabel();
        assembler.branch(H8Condition.EQUAL, same);
        replyAck(STALE_COMMAND, 3);
        assembler.label(same);
        String conflict = code.newLabel();
        assembler.load(PENDING_LEFT, H8Register.R0);
        assembler.load(LEFT.base() + TARGET, H8Register.R1);
        assembler.compare(H8Register.R1, H8Register.R0);
        assembler.branch(H8Condition.NOT_EQUAL, conflict);
        assembler.load(PENDING_RIGHT, H8Register.R0);
        assembler.load(RIGHT.base() + TARGET, H8Register.R1);
        assembler.compare(H8Register.R1, H8Register.R0);
        assembler.branch(H8Condition.NOT_EQUAL, conflict);
        replyAck(DUPLICATE, 3);
        assembler.label(conflict);
        replyAck(ID_CONFLICT, 3);
        assembler.label(newer);
        String idle = code.newLabel();
        assembler.loadByte(STATE, H8ByteRegister.R0L);
        assembler.compareImmediate(RUNNING, H8ByteRegister.R0L);
        assembler.branch(H8Condition.NOT_EQUAL, idle);
        replyAck(BUSY, 3);
        assembler.label(idle);
        String startable = code.newLabel();
        assembler.loadByte(STATE, H8ByteRegister.R0L);
        assembler.compareImmediate(READY, H8ByteRegister.R0L);
        assembler.branch(H8Condition.EQUAL, startable);
        assembler.compareImmediate(COMPLETE, H8ByteRegister.R0L);
        assembler.branch(H8Condition.EQUAL, startable);
        assembler.compareImmediate(STOPPED, H8ByteRegister.R0L);
        assembler.branch(H8Condition.EQUAL, startable);
        replyAck(NOT_ARMED_ACK, 3);
        assembler.label(startable);
        assembler.call(SNAPSHOT);
        for (Side side : SIDES) {
            String inRing = code.newLabel();
            assembler.load(pendingOf(side), H8Register.R0);
            assembler.load(side.base() + POSITION, H8Register.R1);
            assembler.subtract(H8Register.R1, H8Register.R0);
            assembler.moveImmediate(0x8000, H8Register.R1);
            assembler.compare(H8Register.R1, H8Register.R0);
            code.jumpIf(H8Condition.NOT_EQUAL, inRing);
            replyAck(OUT_OF_RANGE, 3);
            assembler.label(inRing);
        }
        assembler.loadByte(RcxCode.REQUEST + 2, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, MOTION);
        for (Side side : SIDES) {
            assembler.load(pendingOf(side), H8Register.R0);
            assembler.store(H8Register.R0, side.base() + TARGET);
            // The distance from here to the target, and the direction it lies in: 0 forward, 1 backward.
            assembler.load(side.base() + POSITION, H8Register.R1);
            assembler.store(H8Register.R1, side.base() + START_POSITION);
            assembler.subtract(H8Register.R1, H8Register.R0);
            assembler.moveImmediate(0, H8ByteRegister.R2L);
            String ahead = code.newLabel();
            assembler.moveImmediate(0, H8Register.R3);
            assembler.compare(H8Register.R3, H8Register.R0);
            code.jumpIf(H8Condition.GREATER_OR_EQUAL, ahead);
            assembler.moveImmediate(1, H8ByteRegister.R2L);
            assembler.subtract(H8Register.R0, H8Register.R3);
            assembler.move(H8Register.R3, H8Register.R0);
            assembler.label(ahead);
            assembler.store(H8Register.R0, side.base() + DISTANCE);
            assembler.storeByte(H8ByteRegister.R2L, side.base() + DIRECTION);
            assembler.load(side.base() + POSITION, H8Register.R0);
            assembler.store(H8Register.R0, side.base() + TRAJECTORY);
            assembler.store(H8Register.R0, side.base() + LAST);
            assembler.moveImmediate(0, H8Register.R0);
            assembler.store(H8Register.R0, side.base() + VELOCITY);
            assembler.store(H8Register.R0, side.base() + FRACTION);
            assembler.store(H8Register.R0, side.base() + HELD_CYCLES);
            assembler.store(H8Register.R0, side.base() + STALL);
            assembler.store(H8Register.R0, side.base() + SINCE_STEP);
            assembler.storeByte(H8ByteRegister.R0L, side.base() + DONE);
            assembler.storeByte(H8ByteRegister.R0L, side.base() + POWER);
            assembler.storeByte(H8ByteRegister.R0L, side.base() + STEPS_TAKEN);
            assembler.moveImmediate(0xFFFF, H8Register.R0);
            assembler.store(H8Register.R0, side.base() + STEP_INTERVAL);
        }
        assembler.moveImmediate(0, H8Register.R0);
        assembler.store(H8Register.R0, ELAPSED);
        assembler.store(H8Register.R0, IDLE);
        assembler.storeByte(H8ByteRegister.R0L, FAULTS);
        storeTimeoutTicks();
        assembler.moveImmediate(RUNNING, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, STATE);
        replyAck(ACCEPTED, 3);
    }

    /** Runs {@link #TICK}: one control cycle, from a fresh analog update. */
    private void tick() {
        assembler.label(TICK);
        String done = code.newLabel();
        assembler.load(CYCLES_RUN, H8Register.R0);
        assembler.adds(1, H8Register.R0);
        assembler.store(H8Register.R0, CYCLES_RUN);
        assembler.moveImmediate(0, H8Register.R0);
        assembler.store(H8Register.R0, IDLE);
        assembler.loadByte(STATE, H8ByteRegister.R0L);
        assembler.compareImmediate(RUNNING, H8ByteRegister.R0L);
        code.jumpIf(H8Condition.NOT_EQUAL, done);
        assembler.call(SNAPSHOT);
        assembler.call(LEFT_SIDE);
        assembler.call(RIGHT_SIDE);
        assembler.load(ELAPSED, H8Register.R0);
        assembler.adds(1, H8Register.R0);
        assembler.store(H8Register.R0, ELAPSED);
        String notStalled = code.newLabel();
        assembler.loadByte(FAULTS, H8ByteRegister.R0L);
        assembler.and(FAULT_STALL, H8ByteRegister.R0L);
        code.jumpIf(H8Condition.EQUAL, notStalled);
        latch(STALLED);
        assembler.jump(done);
        assembler.label(notStalled);
        String notTimedOut = code.newLabel();
        assembler.load(ELAPSED, H8Register.R0);
        assembler.load(TIMEOUT_TICKS, H8Register.R1);
        assembler.compare(H8Register.R1, H8Register.R0);
        code.jumpIf(H8Condition.CARRY_SET, notTimedOut);
        assembler.loadByte(FAULTS, H8ByteRegister.R0L);
        assembler.or(FAULT_TIMEOUT, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, FAULTS);
        latch(TIMED_OUT);
        assembler.jump(done);
        assembler.label(notTimedOut);
        assembler.loadByte(LEFT.base() + DONE, H8ByteRegister.R0L);
        assembler.compareImmediate(1, H8ByteRegister.R0L);
        code.jumpIf(H8Condition.NOT_EQUAL, done);
        assembler.loadByte(RIGHT.base() + DONE, H8ByteRegister.R0L);
        assembler.compareImmediate(1, H8ByteRegister.R0L);
        code.jumpIf(H8Condition.NOT_EQUAL, done);
        assembler.moveImmediate(COMPLETE, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, STATE);
        assembler.label(done);
        assembler.returnFromSubroutine();
    }

    /** Runs {@link #IDLE_TICK}: a main-loop pass with no fresh analog update counts toward the stale limit. */
    private void idleTick() {
        assembler.label(IDLE_TICK);
        String done = code.newLabel();
        assembler.loadByte(STATE, H8ByteRegister.R0L);
        assembler.compareImmediate(RUNNING, H8ByteRegister.R0L);
        code.jumpIf(H8Condition.NOT_EQUAL, done);
        assembler.load(IDLE, H8Register.R0);
        assembler.adds(1, H8Register.R0);
        assembler.store(H8Register.R0, IDLE);
        assembler.moveImmediate(STALE_PASSES, H8Register.R1);
        assembler.compare(H8Register.R1, H8Register.R0);
        code.jumpIf(H8Condition.LOWER_OR_SAME, done);
        assembler.loadByte(FAULTS, H8ByteRegister.R0L);
        assembler.or(FAULT_STALE, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, FAULTS);
        latch(FAULT);
        assembler.label(done);
        assembler.returnFromSubroutine();
    }

    /** Runs {@link #OVERRIDE}: a manual command took A or C; the regulator stops and latches the override. */
    private void override() {
        assembler.label(OVERRIDE);
        assembler.loadByte(FAULTS, H8ByteRegister.R0L);
        assembler.or(FAULT_EXTERNAL_OVERRIDE, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, FAULTS);
        latch(FAULT);
        assembler.returnFromSubroutine();
    }

    /** Runs {@link #REMOTE_STOP}: the remote's stop button ends a running movement. */
    private void remoteStop() {
        assembler.label(REMOTE_STOP);
        String done = code.newLabel();
        assembler.loadByte(STATE, H8ByteRegister.R0L);
        assembler.compareImmediate(RUNNING, H8ByteRegister.R0L);
        code.jumpIf(H8Condition.NOT_EQUAL, done);
        latch(STOPPED);
        assembler.label(done);
        assembler.returnFromSubroutine();
    }

    /**
     * Runs {@link #STOP_OUTPUTS}: records the braking policy for the legacy outputs A and C, then stops both with it.
     * The state must already be the one that ends the movement, so the legacy commands take the outputs back.
     */
    private void stopOutputs() {
        assembler.label(STOP_OUTPUTS);
        String floatPolicy = code.newLabel();
        assembler.moveImmediate(0, H8Register.R1);
        assembler.loadByte(CONFIG, H8ByteRegister.R0L);
        assembler.and(CONFIG_BRAKE, H8ByteRegister.R0L);
        code.jumpIf(H8Condition.EQUAL, floatPolicy);
        assembler.moveImmediate(STOP_MODE_OFF, H8Register.R1);
        assembler.label(floatPolicy);
        assembler.store(H8Register.R1, RcxMotorCommands.MODES);
        assembler.store(H8Register.R1, RcxMotorCommands.MODES + 4);
        for (Side side : SIDES) {
            loadHoldMode(H8Register.R1);
            assembler.moveImmediate(FULL_POWER, H8Register.R2);
            code.rom().call(RcxRom.CONTROL_MOTOR, Argument.constant(side.motor()),
                    Argument.register(H8Register.R1), Argument.register(H8Register.R2));
        }
        assembler.returnFromSubroutine();
    }

    /** Runs one side's control cycle, from its snapshot position. */
    private void side(Side side, String label) {
        assembler.label(label);
        String ret = code.newLabel();
        assembler.loadByte(side.base() + DONE, H8ByteRegister.R0L);
        assembler.compareImmediate(0, H8ByteRegister.R0L);
        code.jumpIf(H8Condition.NOT_EQUAL, ret);
        timeSteps(side);
        // Progress from the start toward the target, in counts (r1), and the distance to cover (r2).
        loadProgress(side, H8Register.R1, H8Register.R0);
        assembler.load(side.base() + DISTANCE, H8Register.R2);
        // Done once the whole distance is covered, or once what remains is within the distance the wheel coasts at its
        // speed: the brake (or float) then stops the wheel on the target.
        String notDone = code.newLabel();
        String finish = code.newLabel();
        assembler.compare(H8Register.R2, H8Register.R1);
        assembler.branch(H8Condition.GREATER_OR_EQUAL, finish);
        brakeEarly(side, notDone);
        assembler.label(finish);
        assembler.moveImmediate(1, H8Register.R0);
        assembler.storeByte(H8ByteRegister.R0L, side.base() + DONE);
        assembler.moveImmediate(NO_POWER, H8Register.R0);
        assembler.storeByte(H8ByteRegister.R0L, side.base() + POWER);
        loadHoldMode(H8Register.R1);
        assembler.moveImmediate(FULL_POWER, H8Register.R2);
        code.rom().call(RcxRom.CONTROL_MOTOR, Argument.constant(side.motor()),
                Argument.register(H8Register.R1), Argument.register(H8Register.R2));
        assembler.jump(ret);
        assembler.label(notDone);
        // The base power is the configured maximum, one level less over the last few counts to limit the overshoot.
        loadByteWord(MAX_POWER, H8Register.R3);
        assembler.move(H8Register.R2, H8Register.R4);
        assembler.subtract(H8Register.R1, H8Register.R4);
        assembler.moveImmediate(SLOW_COUNTS, H8Register.R5);
        assembler.compare(H8Register.R5, H8Register.R4);
        String notSlowing = code.newLabel();
        code.jumpIf(H8Condition.GREATER, notSlowing);
        // Not below one level: at no power the wheel would brake short of its target and never finish.
        assembler.moveImmediate(1, H8Register.R5);
        assembler.compare(H8Register.R5, H8Register.R3);
        code.jumpIf(H8Condition.LOWER_OR_SAME, notSlowing);
        lowerPowerByOne(H8Register.R3);
        assembler.label(notSlowing);
        // Keep the wheels level: a side ahead of the other (still moving) side by a count drops a level, by more brakes.
        // Until the other side has moved at all, a side that has brakes after its first count, so the slower motor's
        // late start does not leave the faster one ahead for the whole move.
        Side other = side == LEFT ? RIGHT : LEFT;
        String level = code.newLabel();
        String hold = code.newLabel();
        assembler.loadByte(other.base() + DONE, H8ByteRegister.R0L);
        assembler.compareImmediate(0, H8ByteRegister.R0L);
        code.jumpIf(H8Condition.NOT_EQUAL, level);
        loadProgress(other, H8Register.R6, H8Register.R0);
        assembler.move(H8Register.R1, H8Register.R4);
        assembler.subtract(H8Register.R6, H8Register.R4);
        assembler.moveImmediate(1, H8Register.R5);
        assembler.compare(H8Register.R5, H8Register.R4);
        code.jumpIf(H8Condition.LESS, level);
        assembler.compare(H8Register.R5, H8Register.R6);
        code.jumpIf(H8Condition.LESS, hold);
        lowerPowerByOne(H8Register.R3);
        assembler.moveImmediate(2, H8Register.R5);
        assembler.compare(H8Register.R5, H8Register.R4);
        code.jumpIf(H8Condition.LESS, level);
        assembler.label(hold);
        assembler.moveImmediate(NO_POWER, H8Register.R3);
        assembler.label(level);
        // Counts the cycles the side is held braked, for the diagnostic command.
        String driven = code.newLabel();
        assembler.moveImmediate(0, H8Register.R0);
        assembler.compare(H8Register.R0, H8Register.R3);
        code.jumpIf(H8Condition.NOT_EQUAL, driven);
        assembler.load(side.base() + HELD_CYCLES, H8Register.R0);
        assembler.adds(1, H8Register.R0);
        assembler.store(H8Register.R0, side.base() + HELD_CYCLES);
        assembler.label(driven);
        assembler.move(H8Register.R3, H8Register.R1);
        assembler.storeByte(H8ByteRegister.R1L, side.base() + POWER);
        // A driven wheel that does not move for the stall window is stalled.
        String stallStore = code.newLabel();
        String noStall = code.newLabel();
        assembler.moveImmediate(0, H8Register.R4);
        assembler.moveImmediate(0, H8Register.R6);
        assembler.compare(H8Register.R6, H8Register.R1);
        assembler.branch(H8Condition.EQUAL, stallStore);
        assembler.load(side.base() + POSITION, H8Register.R2);
        assembler.load(side.base() + LAST, H8Register.R3);
        assembler.compare(H8Register.R3, H8Register.R2);
        assembler.branch(H8Condition.NOT_EQUAL, stallStore);
        assembler.load(side.base() + STALL, H8Register.R4);
        assembler.moveImmediate(1, H8Register.R6);
        assembler.add(H8Register.R6, H8Register.R4);
        assembler.label(stallStore);
        assembler.store(H8Register.R4, side.base() + STALL);
        assembler.load(side.base() + POSITION, H8Register.R2);
        assembler.store(H8Register.R2, side.base() + LAST);
        assembler.moveImmediate(STALL_TICKS, H8Register.R6);
        assembler.compare(H8Register.R6, H8Register.R4);
        assembler.branch(H8Condition.CARRY_SET, noStall);
        assembler.loadByte(FAULTS, H8ByteRegister.R2L);
        assembler.or(side.stallFault(), H8ByteRegister.R2L);
        assembler.storeByte(H8ByteRegister.R2L, FAULTS);
        assembler.label(noStall);
        // The output runs in the direction, flipped for a reversed motor. No power brakes it, at full braking power:
        // the ROM brakes with the power it is given, so a brake at power 0 let a waiting wheel coast on ahead.
        assembler.moveImmediate(RcxRom.MOTOR_FORWARD, H8Register.R4);
        loadByteWord(side.base() + DIRECTION, H8Register.R5);
        assembler.loadByte(CONFIG, H8ByteRegister.R6L);
        assembler.and(side.motorInvert(), H8ByteRegister.R6L);
        String notInverted = code.newLabel();
        assembler.branch(H8Condition.EQUAL, notInverted);
        assembler.moveImmediate(1, H8Register.R6);
        assembler.subtract(H8Register.R5, H8Register.R6);
        assembler.move(H8Register.R6, H8Register.R5);
        assembler.label(notInverted);
        assembler.add(H8Register.R5, H8Register.R4);
        assembler.moveImmediate(0, H8Register.R6);
        assembler.compare(H8Register.R6, H8Register.R1);
        String drive = code.newLabel();
        assembler.branch(H8Condition.NOT_EQUAL, drive);
        assembler.moveImmediate(RcxRom.MOTOR_BRAKE, H8Register.R4);
        assembler.moveImmediate(FULL_POWER, H8Register.R1);
        assembler.label(drive);
        code.rom().call(RcxRom.CONTROL_MOTOR, Argument.constant(side.motor()),
                Argument.register(H8Register.R4), Argument.register(H8Register.R1));
        assembler.label(ret);
        assembler.returnFromSubroutine();
    }

    /**
     * Times the side's rotation-sensor steps: a cycle whose position differs from the last one's is a step, and from the
     * second step on, the cycles since the previous one become the side's step interval. Clobbers r0 and r1.
     */
    private void timeSteps(Side side) {
        String still = code.newLabel();
        String timed = code.newLabel();
        assembler.load(side.base() + SINCE_STEP, H8Register.R0);
        assembler.adds(1, H8Register.R0);
        assembler.store(H8Register.R0, side.base() + SINCE_STEP);
        assembler.load(side.base() + POSITION, H8Register.R0);
        assembler.load(side.base() + LAST, H8Register.R1);
        assembler.compare(H8Register.R1, H8Register.R0);
        assembler.branch(H8Condition.EQUAL, still);
        assembler.loadByte(side.base() + STEPS_TAKEN, H8ByteRegister.R0L);
        assembler.compareImmediate(0, H8ByteRegister.R0L);
        assembler.branch(H8Condition.EQUAL, timed);
        assembler.load(side.base() + SINCE_STEP, H8Register.R0);
        assembler.store(H8Register.R0, side.base() + STEP_INTERVAL);
        assembler.label(timed);
        assembler.moveImmediate(1, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, side.base() + STEPS_TAKEN);
        assembler.moveImmediate(0, H8Register.R0);
        assembler.store(H8Register.R0, side.base() + SINCE_STEP);
        assembler.label(still);
    }

    /**
     * Falls through to finish the side once it will reach its target within the brake lead: its remaining counts,
     * distance (r2) minus progress (r1), times its cycles per step, less the cycles since its last step, is at most the
     * lead. So it brakes to the cycle, not to the step, and two wheels a fraction of a step apart in speed brake a
     * fraction of a step apart. With the lead as {@code q} steps of the interval plus {@code r} cycles, that is: at most
     * {@code q} counts remain, or {@code q + 1} remain and at least {@code interval - r} cycles have passed since the
     * last step.
     *
     * <p>The cycles per step are the longer of the last step interval and the cycles since the last step, so a side
     * slowing down is not taken at its earlier speed; the cycles since the last step refine the time only while within
     * the last interval. Jumps to {@code notDone} otherwise, or when the side is slower than a step per
     * {@link #MAX_TIMED_INTERVAL} cycles. Keeps r1 and r2.
     */
    private void brakeEarly(Side side, String notDone) {
        String slowest = code.newLabel();
        String early = code.newLabel();
        assembler.load(side.base() + STEP_INTERVAL, H8Register.R5);
        assembler.load(side.base() + SINCE_STEP, H8Register.R0);
        assembler.compare(H8Register.R5, H8Register.R0);
        assembler.branch(H8Condition.LOWER_OR_SAME, slowest);
        assembler.move(H8Register.R0, H8Register.R5);
        assembler.label(slowest);
        assembler.moveImmediate(MAX_TIMED_INTERVAL, H8Register.R0);
        assembler.compare(H8Register.R0, H8Register.R5);
        assembler.branch(H8Condition.HIGHER, notDone);
        // The lead is a byte and the interval at least one cycle, so the quotient fits the divide's byte: q in r0l and
        // the remainder r in r0h, moved to r6.
        loadByteWord(BRAKE_LEAD, H8Register.R0);
        // A lead of 0 turns the early brake off: the time rule would still take an overdue step as due now.
        assembler.moveImmediate(0, H8Register.R3);
        assembler.compare(H8Register.R3, H8Register.R0);
        assembler.branch(H8Condition.EQUAL, notDone);
        assembler.divideUnsigned(H8ByteRegister.R5L, H8Register.R0);
        assembler.moveImmediate(0, H8ByteRegister.R6H);
        assembler.move(H8ByteRegister.R0H, H8ByteRegister.R6L);
        assembler.moveImmediate(0, H8ByteRegister.R0H);
        assembler.move(H8Register.R2, H8Register.R4);
        assembler.subtract(H8Register.R1, H8Register.R4);
        assembler.compare(H8Register.R0, H8Register.R4);
        assembler.branch(H8Condition.LOWER_OR_SAME, early);
        assembler.adds(1, H8Register.R0);
        assembler.compare(H8Register.R0, H8Register.R4);
        assembler.branch(H8Condition.NOT_EQUAL, notDone);
        // One count more than the lead's whole steps: brake once the next step is due within the remainder r.
        assembler.load(side.base() + STEP_INTERVAL, H8Register.R3);
        assembler.load(side.base() + SINCE_STEP, H8Register.R0);
        assembler.compare(H8Register.R3, H8Register.R0);
        assembler.branch(H8Condition.HIGHER, notDone);
        assembler.subtract(H8Register.R6, H8Register.R3);
        assembler.compare(H8Register.R3, H8Register.R0);
        assembler.branch(H8Condition.CARRY_SET, notDone);
        assembler.label(early);
    }

    /** Runs the snapshot: each side's position, from its sensor's converted value, flipped if the sensor is inverted. */
    private void snapshot() {
        assembler.label(SNAPSHOT);
        for (Side side : SIDES) {
            assembler.load(side.sensor() + SENSOR_VALUE, H8Register.R0);
            assembler.loadByte(CONFIG, H8ByteRegister.R1L);
            assembler.and(side.sensorInvert(), H8ByteRegister.R1L);
            String kept = code.newLabel();
            assembler.branch(H8Condition.EQUAL, kept);
            assembler.moveImmediate(0, H8Register.R1);
            assembler.subtract(H8Register.R0, H8Register.R1);
            assembler.move(H8Register.R1, H8Register.R0);
            assembler.label(kept);
            assembler.store(H8Register.R0, side.base() + POSITION);
        }
        assembler.returnFromSubroutine();
    }

    /** Sets the mode the ROM takes for a stop, the brake or the float, from the braking policy, into {@code mode}. */
    private void loadHoldMode(H8Register mode) {
        String floatMode = code.newLabel();
        assembler.moveImmediate(RcxRom.MOTOR_BRAKE, mode);
        assembler.loadByte(CONFIG, H8ByteRegister.R6L);
        assembler.and(CONFIG_BRAKE, H8ByteRegister.R6L);
        code.jumpIf(H8Condition.NOT_EQUAL, floatMode);
        assembler.moveImmediate(RcxRom.MOTOR_FLOAT, mode);
        assembler.label(floatMode);
    }

    /** Stores the state the regulator ends its movement in, and stops both outputs by the braking policy. */
    private void latch(int state) {
        assembler.moveImmediate(state, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, STATE);
        assembler.call(STOP_OUTPUTS);
    }

    /** Clears the regulator's history for a new session: faults, the motion, counts and both sides' motion state. */
    private void clearHistory() {
        assembler.moveImmediate(0, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, MOTION);
        assembler.storeByte(H8ByteRegister.R0L, FAULTS);
        assembler.moveImmediate(0, H8Register.R0);
        assembler.store(H8Register.R0, ELAPSED);
        assembler.store(H8Register.R0, IDLE);
        for (Side side : SIDES) {
            assembler.store(H8Register.R0, side.base() + VELOCITY);
            assembler.store(H8Register.R0, side.base() + FRACTION);
            assembler.store(H8Register.R0, side.base() + HELD_CYCLES);
            assembler.store(H8Register.R0, side.base() + STALL);
            assembler.storeByte(H8ByteRegister.R0L, side.base() + DONE);
            assembler.storeByte(H8ByteRegister.R0L, side.base() + POWER);
            assembler.storeByte(H8ByteRegister.R0L, side.base() + DIRECTION);
        }
    }

    /** Makes input {@code input} a rotation sensor, zeroed, and powers it (the ROM's active sensor for light and rotation). */
    private void configureRotation(int input) {
        int sensor = RcxSensorCommands.SENSORS + input * SENSOR_SIZE;
        assembler.moveImmediate(SENSOR_TYPE_ROTATION, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, sensor);
        assembler.moveImmediate(ROTATION_MODE, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, sensor + 1);
        assembler.moveImmediate(0, H8Register.R0);
        assembler.store(H8Register.R0, sensor + 2);
        assembler.store(H8Register.R0, sensor + SENSOR_VALUE);
        assembler.storeByte(H8ByteRegister.R0L, sensor + 6);
        assembler.moveImmediate(input, H8Register.R2);
        assembler.moveImmediate(sensor, H8Register.R4);
        assembler.call(RcxSensorCommands.POWER_ONE);
    }

    /** Requires the session in {@code REQUEST+1} to be the armed one, or replies with the rejection and leaves. */
    private void requireSession(int arguments) {
        String matched = code.newLabel();
        loadByteWord(RcxCode.REQUEST + 1, H8Register.R0);
        loadByteWord(SESSION, H8Register.R1);
        assembler.compare(H8Register.R1, H8Register.R0);
        assembler.branch(H8Condition.EQUAL, matched);
        String invalid = code.newLabel();
        assembler.loadByte(STATE, H8ByteRegister.R0L);
        assembler.compareImmediate(UNARMED, H8ByteRegister.R0L);
        assembler.branch(H8Condition.NOT_EQUAL, invalid);
        replyAck(NOT_ARMED_ACK, arguments);
        assembler.label(invalid);
        replyAck(INVALID_SESSION, arguments);
        assembler.label(matched);
    }

    /** Requires the motion in {@code REQUEST+2} to be the armed session's current motion, or replies with {@code ack}. */
    private void requireMotion(int ack, int arguments) {
        String same = code.newLabel();
        loadByteWord(RcxCode.REQUEST + 2, H8Register.R0);
        loadByteWord(MOTION, H8Register.R1);
        assembler.compare(H8Register.R1, H8Register.R0);
        assembler.branch(H8Condition.EQUAL, same);
        replyAck(ack, arguments);
        assembler.label(same);
    }

    /**
     * Checks one profile rate at {@code argument} (low byte first): it must be positive and within the byte range once
     * divided by {@link #TICKS_PER_SECOND}.
     */
    private void checkRate(int argument) {
        loadWordArgument(argument, H8Register.R0);
        assembler.moveImmediate(0, H8Register.R1);
        assembler.compare(H8Register.R1, H8Register.R0);
        String nonZero = code.newLabel();
        assembler.branch(H8Condition.NOT_EQUAL, nonZero);
        replyAck(INVALID_ARGUMENT, 1);
        assembler.label(nonZero);
        assembler.moveImmediate(MAX_RATE, H8Register.R1);
        assembler.compare(H8Register.R1, H8Register.R0);
        String inRange = code.newLabel();
        assembler.branch(H8Condition.LOWER_OR_SAME, inRange);
        replyAck(OUT_OF_RANGE, 1);
        assembler.label(inRange);
    }

    /**
     * Stores the rate at {@code argument} as given, and per control cycle in 1/256 counts (Q8) in {@code perTick}: the
     * rate times 256 / {@link #TICKS_PER_SECOND}, 0.769, computed as {@code rate - rate / 4 + rate / 64}, 0.766, since the
     * H8 here has a divide but no multiply. {@link #perCycleQ8} is the same arithmetic, for the tests.
     */
    private void storeRate(int argument, int rate, int perTick) {
        loadWordArgument(argument, H8Register.R0);
        assembler.store(H8Register.R0, rate);
        assembler.move(H8Register.R0, H8Register.R2);
        assembler.moveImmediate(4, H8ByteRegister.R6L);
        assembler.divideUnsigned(H8ByteRegister.R6L, H8Register.R2);
        assembler.moveImmediate(0, H8ByteRegister.R2H);
        assembler.move(H8Register.R0, H8Register.R3);
        assembler.moveImmediate(64, H8ByteRegister.R6L);
        assembler.divideUnsigned(H8ByteRegister.R6L, H8Register.R3);
        assembler.moveImmediate(0, H8ByteRegister.R3H);
        assembler.subtract(H8Register.R2, H8Register.R0);
        assembler.add(H8Register.R3, H8Register.R0);
        assembler.store(H8Register.R0, perTick);
    }

    /** A rate in counts per second as the firmware converts it to 1/256 counts per control cycle. */
    static int perCycleQ8(int ratePerSecond) {
        return ratePerSecond - ratePerSecond / 4 + ratePerSecond / 64;
    }

    /**
     * Stores the timeout in control cycles, {@code TIMEOUT_SECONDS * 333}, as {@code seconds * 256 + seconds * 77} by
     * doubling and adding: 77 is 64 + 8 + 4 + 1. The acceleration command keeps the seconds within a word's worth.
     */
    private void storeTimeoutTicks() {
        if (TICKS_PER_SECOND != 256 + 64 + 8 + 4 + 1) {
            throw new IllegalStateException("storeTimeoutTicks multiplies by 333 only");
        }
        loadByteWord(TIMEOUT_SECONDS, H8Register.R2);
        // r1 = seconds * 256: the seconds in the high byte.
        assembler.loadByte(TIMEOUT_SECONDS, H8ByteRegister.R1H);
        assembler.moveImmediate(0, H8ByteRegister.R1L);
        assembler.add(H8Register.R2, H8Register.R1);
        int doubled = 1;
        for (int multiple : new int[] {4, 8, 64}) {
            while (doubled < multiple) {
                assembler.add(H8Register.R2, H8Register.R2);
                doubled *= 2;
            }
            assembler.add(H8Register.R2, H8Register.R1);
        }
        assembler.store(H8Register.R1, TIMEOUT_TICKS);
    }

    /**
     * Replies {@code ack} with {@code arguments} argument bytes (1 or 3): the acknowledgement in r0l, then the shared
     * tail that echoes the session and motion. The tails are shared so each reply is a load and a jump.
     */
    private void replyAck(int ack, int arguments) {
        assembler.moveImmediate(ack, H8ByteRegister.R0L);
        assembler.jump(arguments == 1 ? ACK_ONE : ACK_THREE);
    }

    /** Emits the two shared reply tails: an acknowledgement with one argument, and with three echoing the request. */
    private void sharedAcknowledgements() {
        assembler.label(ACK_ONE);
        assembler.storeByte(H8ByteRegister.R0L, RcxCode.REPLY + 1);
        code.replyWith(2);
        assembler.label(ACK_THREE);
        assembler.storeByte(H8ByteRegister.R0L, RcxCode.REPLY + 1);
        assembler.loadByte(RcxCode.REQUEST + 1, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, RcxCode.REPLY + 2);
        assembler.loadByte(RcxCode.REQUEST + 2, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, RcxCode.REPLY + 3);
        code.replyWith(4);
    }

    /** Replies to ARM: the acknowledgement, the protocol version and the mapping. */
    private void replyArm(int ack) {
        assembler.moveImmediate(ack, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, RcxCode.REPLY + 1);
        assembler.moveImmediate(PROTOCOL_VERSION, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, RcxCode.REPLY + 2);
        assembler.moveImmediate(MAPPING, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, RcxCode.REPLY + 3);
        code.replyWith(4);
    }

    /**
     * Loads the side's progress from its start toward its target into {@code destination}, signed counts: the position
     * minus the start, negated for a side that moves backward. Clobbers {@code scratch}.
     */
    private void loadProgress(Side side, H8Register destination, H8Register scratch) {
        assembler.load(side.base() + POSITION, destination);
        assembler.load(side.base() + START_POSITION, scratch);
        assembler.subtract(scratch, destination);
        String forward = code.newLabel();
        assembler.loadByte(side.base() + DIRECTION, H8ByteRegister.low(scratch));
        assembler.compareImmediate(0, H8ByteRegister.low(scratch));
        code.jumpIf(H8Condition.EQUAL, forward);
        assembler.moveImmediate(0, scratch);
        assembler.subtract(destination, scratch);
        assembler.move(scratch, destination);
        assembler.label(forward);
    }

    /** Lowers the power in {@code power} by one level, not below zero. */
    private void lowerPowerByOne(H8Register power) {
        String zero = code.newLabel();
        assembler.moveImmediate(0, H8Register.R0);
        assembler.compare(H8Register.R0, power);
        code.jumpIf(H8Condition.EQUAL, zero);
        assembler.subs(1, power);
        assembler.label(zero);
    }

    private void replyByte(int address, int index) {
        assembler.loadByte(address, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, RcxCode.REPLY + index);
    }

    /** Replies with the word at {@code address} as two argument bytes, low byte first. */
    private void replyWord(int address, int index) {
        assembler.load(address, H8Register.R0);
        assembler.storeByte(H8ByteRegister.R0L, RcxCode.REPLY + index);
        assembler.storeByte(H8ByteRegister.R0H, RcxCode.REPLY + index + 1);
    }

    /** Loads the word argument whose low byte is at {@code low} (high byte after it) into {@code destination}. */
    private void loadWordArgument(int low, H8Register destination) {
        assembler.loadByte(low + 1, H8ByteRegister.high(destination));
        assembler.loadByte(low, H8ByteRegister.low(destination));
    }

    /** Loads the byte at {@code address} into {@code destination}, with the high byte zeroed. */
    private void loadByteWord(int address, H8Register destination) {
        assembler.loadByte(address, H8ByteRegister.low(destination));
        assembler.moveImmediate(0, H8ByteRegister.high(destination));
    }


    private static int pendingOf(Side side) {
        return side == LEFT ? PENDING_LEFT : PENDING_RIGHT;
    }
}
