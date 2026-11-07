package io.github.jabrena.juno.backend.h8300;

import io.github.jabrena.juno.backend.h8300.RcxRom.Argument;

/**
 * The regulated differential drive of {@link RcxRemoteFirmware}: outputs A and C, the left and right wheels, each
 * regulated locally by the firmware from its rotation sensor (inputs 1 and 3), so infrared is a command channel and not
 * a control loop. The commands are 0x61 status, 0x62 stop, 0x63 arm, 0x67 profile and 0x76 start; the acknowledgements,
 * states and payloads are the ones {@code RcxRegulatedProtocol} expects on the host (OpenSpec change
 * {@code rcx-regulated-differential-drive}).
 *
 * <p>The regulator runs once per fresh analog update, after the sensors are read. Each side follows a trajectory that
 * accelerates toward its target and then applies bounded proportional power to its error (the spec's
 * {@code (KP_Q8 * |e| + 128) >> 8} with {@code KP_Q8 = 64}, which is {@code (|e| + 2) / 4} for integers), clamped to the
 * minimum starting power and the configured maximum, with a slew limit on the rise and a zero-power reversal.
 *
 * <p>The values marked as placeholders below are not measured yet (tasks 1.3 and 1.6): the minimum starting power, the
 * slew, the stall and stale windows, and the time base, which is the analog-update cadence rather than a 100 ms timer.
 * Profile speed and acceleration arrive in counts per second and are divided by {@link #TICKS_PER_SECOND} once, when
 * the profile is set, so the control loop works in counts per tick.
 */
final class RcxRegulator {
    static final int OPCODE_STATUS = 0x61;
    static final int OPCODE_STOP = 0x62;
    static final int OPCODE_ARM = 0x63;
    static final int OPCODE_PROFILE = 0x67;
    static final int OPCODE_START = 0x76;

    static final String STATUS = "reg_status";
    static final String STOP = "reg_stop";
    static final String ARM = "reg_arm";
    static final String PROFILE = "reg_profile";
    static final String START = "reg_start";
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

    /** Placeholder (task 1.6): proportional power is {@code (|e| + 2) / 4}, that is {@code KP_Q8 = 64}. */
    static final int KP_DIVISOR = 4;
    /** Placeholder: the error saturates here, where proportional power reaches the byte range. */
    static final int SATURATED_ERROR = 1019;
    /** Placeholder (task 1.6): an error this small or smaller applies no power, so the wheel holds instead of hunting. */
    static final int DEADBAND = 1;
    /** Placeholder: the least power that moves a wheel from rest. */
    static final int MIN_START_POWER = 2;
    /** Placeholder: the most the power may rise in one cycle. */
    static final int SLEW_PER_TICK = 1;
    /** Placeholder (task 1.3): control cycles per second, the rate that converts profile units to per-cycle units. */
    static final int TICKS_PER_SECOND = 20;
    /** Placeholder: cycles a driven wheel may stay still before it counts as stalled. */
    static final int STALL_TICKS = 20;
    /** Placeholder: main-loop passes without a fresh analog update before the sensors count as stale. */
    static final int STALE_PASSES = 200;
    /** Profile speed and acceleration arrive in counts per second; more than this would not divide into a byte. */
    static final int MAX_RATE = 255 * TICKS_PER_SECOND;

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
    static final int SPEED_TICK = BASE + 12;
    static final int ACCELERATION_TICK = BASE + 14;
    static final int ELAPSED = BASE + 16;
    static final int TIMEOUT_TICKS = BASE + 18;
    static final int IDLE = BASE + 20;

    /** Per side: done, power, direction, stall count, target, trajectory, velocity, position and last position. */
    static final int DONE = 0;
    static final int POWER = 1;
    static final int DIRECTION = 2;
    static final int STALL = 4;
    static final int TARGET = 6;
    static final int TRAJECTORY = 8;
    static final int VELOCITY = 10;
    static final int POSITION = 12;
    static final int LAST = 14;

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

    private static final int LEFT_TARGET_ARGUMENT = RcxCode.REQUEST + 3;
    private static final int RIGHT_TARGET_ARGUMENT = RcxCode.REQUEST + 5;

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
        profile();
        start();
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

    private void status() {
        assembler.label(STATUS);
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
        assembler.call(STOP_OUTPUTS);
        configureRotation(0);
        configureRotation(2);
        assembler.moveImmediate(READY, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, STATE);
        replyArm(ACCEPTED);
    }

    private void profile() {
        assembler.label(PROFILE);
        requireSession(1);
        String notRunning = code.newLabel();
        assembler.loadByte(STATE, H8ByteRegister.R0L);
        assembler.compareImmediate(RUNNING, H8ByteRegister.R0L);
        assembler.branch(H8Condition.NOT_EQUAL, notRunning);
        replyAck(BUSY, 1);
        assembler.label(notRunning);
        checkRate(RcxCode.REQUEST + 2);
        checkRate(RcxCode.REQUEST + 4);
        assembler.loadByte(RcxCode.REQUEST + 7, H8ByteRegister.R0L);
        assembler.compareImmediate(0, H8ByteRegister.R0L);
        String timeout = code.newLabel();
        assembler.branch(H8Condition.NOT_EQUAL, timeout);
        replyAck(INVALID_ARGUMENT, 1);
        assembler.label(timeout);
        assembler.storeByte(H8ByteRegister.R0L, TIMEOUT_SECONDS);
        assembler.loadByte(RcxCode.REQUEST + 6, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, TOLERANCE);
        storeRate(RcxCode.REQUEST + 2, SPEED, SPEED_TICK);
        storeRate(RcxCode.REQUEST + 4, ACCELERATION, ACCELERATION_TICK);
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
        loadWordArgument(LEFT_TARGET_ARGUMENT, H8Register.R0);
        assembler.load(LEFT.base() + TARGET, H8Register.R1);
        assembler.compare(H8Register.R1, H8Register.R0);
        assembler.branch(H8Condition.NOT_EQUAL, conflict);
        loadWordArgument(RIGHT_TARGET_ARGUMENT, H8Register.R0);
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
            loadWordArgument(argumentOf(side), H8Register.R0);
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
            loadWordArgument(argumentOf(side), H8Register.R0);
            assembler.store(H8Register.R0, side.base() + TARGET);
            assembler.load(side.base() + POSITION, H8Register.R0);
            assembler.store(H8Register.R0, side.base() + TRAJECTORY);
            assembler.store(H8Register.R0, side.base() + LAST);
            assembler.moveImmediate(0, H8Register.R0);
            assembler.store(H8Register.R0, side.base() + VELOCITY);
            assembler.store(H8Register.R0, side.base() + STALL);
            assembler.storeByte(H8ByteRegister.R0L, side.base() + DONE);
            assembler.storeByte(H8ByteRegister.R0L, side.base() + POWER);
            assembler.storeByte(H8ByteRegister.R0L, side.base() + DIRECTION);
        }
        assembler.moveImmediate(0, H8Register.R0);
        assembler.store(H8Register.R0, ELAPSED);
        assembler.store(H8Register.R0, IDLE);
        assembler.storeByte(H8ByteRegister.R0L, FAULTS);
        loadByteWord(TIMEOUT_SECONDS, H8Register.R0);
        assembler.moveImmediate(0, H8Register.R1);
        for (int tick = 0; tick < TICKS_PER_SECOND; tick++) {
            assembler.add(H8Register.R0, H8Register.R1);
        }
        assembler.store(H8Register.R1, TIMEOUT_TICKS);
        assembler.moveImmediate(RUNNING, H8ByteRegister.R0L);
        assembler.storeByte(H8ByteRegister.R0L, STATE);
        replyAck(ACCEPTED, 3);
    }

    /** Runs {@link #TICK}: one control cycle, from a fresh analog update. */
    private void tick() {
        assembler.label(TICK);
        String done = code.newLabel();
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
        // The trajectory's remaining distance, target minus trajectory, in r1; the sign of that is kept in r5.
        assembler.load(side.base() + TARGET, H8Register.R1);
        assembler.load(side.base() + TRAJECTORY, H8Register.R2);
        assembler.subtract(H8Register.R2, H8Register.R1);
        // The velocity grows by the acceleration each cycle, up to the speed.
        assembler.load(side.base() + VELOCITY, H8Register.R3);
        assembler.load(ACCELERATION_TICK, H8Register.R4);
        assembler.add(H8Register.R4, H8Register.R3);
        assembler.load(SPEED_TICK, H8Register.R4);
        assembler.compare(H8Register.R4, H8Register.R3);
        String velocityCapped = code.newLabel();
        assembler.branch(H8Condition.LOWER_OR_SAME, velocityCapped);
        assembler.move(H8Register.R4, H8Register.R3);
        assembler.label(velocityCapped);
        assembler.store(H8Register.R3, side.base() + VELOCITY);
        // The step is the smaller of the velocity and the remaining distance, in the remaining distance's direction.
        assembler.moveImmediate(0, H8Register.R5);
        assembler.moveImmediate(0, H8Register.R4);
        assembler.compare(H8Register.R4, H8Register.R1);
        String ahead = code.newLabel();
        assembler.branch(H8Condition.GREATER_OR_EQUAL, ahead);
        assembler.moveImmediate(1, H8Register.R5);
        assembler.moveImmediate(0, H8Register.R4);
        assembler.subtract(H8Register.R1, H8Register.R4);
        assembler.move(H8Register.R4, H8Register.R1);
        assembler.label(ahead);
        assembler.compare(H8Register.R3, H8Register.R1);
        String smaller = code.newLabel();
        assembler.branch(H8Condition.LOWER_OR_SAME, smaller);
        assembler.move(H8Register.R3, H8Register.R1);
        assembler.label(smaller);
        assembler.load(side.base() + TRAJECTORY, H8Register.R2);
        assembler.moveImmediate(0, H8Register.R4);
        assembler.compare(H8Register.R4, H8Register.R5);
        String forward = code.newLabel();
        String moved = code.newLabel();
        assembler.branch(H8Condition.EQUAL, forward);
        assembler.subtract(H8Register.R1, H8Register.R2);
        assembler.jump(moved);
        assembler.label(forward);
        assembler.add(H8Register.R1, H8Register.R2);
        assembler.label(moved);
        assembler.store(H8Register.R2, side.base() + TRAJECTORY);
        // Done when the trajectory has reached the target and the position is within the tolerance.
        String notDone = code.newLabel();
        assembler.load(side.base() + TRAJECTORY, H8Register.R1);
        assembler.load(side.base() + TARGET, H8Register.R2);
        assembler.compare(H8Register.R2, H8Register.R1);
        assembler.branch(H8Condition.NOT_EQUAL, notDone);
        assembler.load(side.base() + TARGET, H8Register.R1);
        assembler.load(side.base() + POSITION, H8Register.R2);
        assembler.subtract(H8Register.R2, H8Register.R1);
        absolute(H8Register.R1);
        loadByteWord(TOLERANCE, H8Register.R2);
        assembler.compare(H8Register.R2, H8Register.R1);
        assembler.branch(H8Condition.HIGHER, notDone);
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
        // The error is the trajectory minus the position; the power is bounded proportional to its size.
        assembler.load(side.base() + TRAJECTORY, H8Register.R1);
        assembler.load(side.base() + POSITION, H8Register.R2);
        assembler.subtract(H8Register.R2, H8Register.R1);
        assembler.moveImmediate(0, H8Register.R5);
        assembler.moveImmediate(0, H8Register.R4);
        assembler.compare(H8Register.R4, H8Register.R1);
        String errorAhead = code.newLabel();
        assembler.branch(H8Condition.GREATER_OR_EQUAL, errorAhead);
        assembler.moveImmediate(1, H8Register.R5);
        assembler.label(errorAhead);
        absolute(H8Register.R1);
        String zeroPower = code.newLabel();
        assembler.moveImmediate(DEADBAND, H8Register.R4);
        assembler.compare(H8Register.R4, H8Register.R1);
        code.jumpIf(H8Condition.LOWER_OR_SAME, zeroPower);
        assembler.moveImmediate(SATURATED_ERROR, H8Register.R4);
        assembler.compare(H8Register.R4, H8Register.R1);
        String unsaturated = code.newLabel();
        assembler.branch(H8Condition.LOWER_OR_SAME, unsaturated);
        assembler.move(H8Register.R4, H8Register.R1);
        assembler.label(unsaturated);
        assembler.moveImmediate(2, H8Register.R4);
        assembler.add(H8Register.R4, H8Register.R1);
        assembler.moveImmediate(KP_DIVISOR, H8ByteRegister.R6L);
        assembler.divideUnsigned(H8ByteRegister.R6L, H8Register.R1);
        assembler.moveImmediate(0, H8ByteRegister.R1H);
        assembler.moveImmediate(MIN_START_POWER, H8Register.R4);
        assembler.compare(H8Register.R4, H8Register.R1);
        String aboveMinimum = code.newLabel();
        assembler.branch(H8Condition.CARRY_CLEAR, aboveMinimum);
        assembler.move(H8Register.R4, H8Register.R1);
        assembler.label(aboveMinimum);
        loadByteWord(MAX_POWER, H8Register.R4);
        assembler.compare(H8Register.R4, H8Register.R1);
        String withinMaximum = code.newLabel();
        assembler.branch(H8Condition.LOWER_OR_SAME, withinMaximum);
        assembler.move(H8Register.R4, H8Register.R1);
        assembler.label(withinMaximum);
        // The power rises by at most the slew limit, and at least to the minimum starting power.
        loadByteWord(side.base() + POWER, H8Register.R4);
        assembler.moveImmediate(SLEW_PER_TICK, H8Register.R6);
        assembler.add(H8Register.R6, H8Register.R4);
        assembler.moveImmediate(MIN_START_POWER, H8Register.R6);
        assembler.compare(H8Register.R6, H8Register.R4);
        String slewFloor = code.newLabel();
        assembler.branch(H8Condition.CARRY_CLEAR, slewFloor);
        assembler.move(H8Register.R6, H8Register.R4);
        assembler.label(slewFloor);
        assembler.compare(H8Register.R4, H8Register.R1);
        String slewed = code.newLabel();
        assembler.branch(H8Condition.LOWER_OR_SAME, slewed);
        assembler.move(H8Register.R4, H8Register.R1);
        assembler.label(slewed);
        // A reversal drops the power to zero first, and the direction changes once the power is zero.
        String setDirection = code.newLabel();
        String keepDirection = code.newLabel();
        loadByteWord(side.base() + DIRECTION, H8Register.R4);
        assembler.compare(H8Register.R5, H8Register.R4);
        assembler.branch(H8Condition.EQUAL, setDirection);
        loadByteWord(side.base() + POWER, H8Register.R4);
        assembler.moveImmediate(0, H8Register.R6);
        assembler.compare(H8Register.R6, H8Register.R4);
        assembler.branch(H8Condition.EQUAL, setDirection);
        assembler.moveImmediate(0, H8Register.R1);
        assembler.jump(keepDirection);
        assembler.label(setDirection);
        assembler.storeByte(H8ByteRegister.R5L, side.base() + DIRECTION);
        assembler.jump(keepDirection);
        assembler.label(zeroPower);
        assembler.moveImmediate(0, H8Register.R1);
        assembler.label(keepDirection);
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
        // The output runs in the direction, flipped for a reversed motor; no power brakes it.
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
        assembler.label(drive);
        code.rom().call(RcxRom.CONTROL_MOTOR, Argument.constant(side.motor()),
                Argument.register(H8Register.R4), Argument.register(H8Register.R1));
        assembler.label(ret);
        assembler.returnFromSubroutine();
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

    /** Stores the rate at {@code argument} as given, and per control cycle, at least one, in {@code perTick}. */
    private void storeRate(int argument, int rate, int perTick) {
        loadWordArgument(argument, H8Register.R0);
        assembler.store(H8Register.R0, rate);
        assembler.moveImmediate(TICKS_PER_SECOND, H8ByteRegister.R1L);
        assembler.divideUnsigned(H8ByteRegister.R1L, H8Register.R0);
        assembler.moveImmediate(0, H8ByteRegister.R0H);
        assembler.moveImmediate(0, H8Register.R1);
        assembler.compare(H8Register.R1, H8Register.R0);
        String nonZero = code.newLabel();
        assembler.branch(H8Condition.NOT_EQUAL, nonZero);
        assembler.moveImmediate(1, H8Register.R0);
        assembler.label(nonZero);
        assembler.store(H8Register.R0, perTick);
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

    /** Replaces {@code register} with its magnitude, as a signed word. */
    private void absolute(H8Register register) {
        String positive = code.newLabel();
        assembler.moveImmediate(0, H8Register.R6);
        assembler.compare(H8Register.R6, register);
        code.jumpIf(H8Condition.GREATER_OR_EQUAL, positive);
        assembler.moveImmediate(0, H8Register.R6);
        assembler.subtract(register, H8Register.R6);
        assembler.move(H8Register.R6, register);
        assembler.label(positive);
    }

    private static int argumentOf(Side side) {
        return side == LEFT ? LEFT_TARGET_ARGUMENT : RIGHT_TARGET_ARGUMENT;
    }
}
