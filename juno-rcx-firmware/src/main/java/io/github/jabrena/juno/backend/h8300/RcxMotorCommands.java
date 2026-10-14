package io.github.jabrena.juno.backend.h8300;

import io.github.jabrena.juno.backend.h8300.RcxRom.Argument;

/**
 * The output commands of {@link RcxRemoteFirmware}: on/off/float (0x21), direction (0xE1) and power (0x13), each
 * selecting outputs A, B, C with the low three bits of its first argument. The firmware keeps every output's LEGO
 * state and hands all three to the ROM's {@code control_motor} after each change.
 */
final class RcxMotorCommands {
    static final String ON_OFF_FLOAT = "on_off_float";
    static final String DIRECTION = "direction";
    static final String POWER = "power";
    static final String APPLY = "apply_motors";

    /** Per output A, B, C, one word each: the LEGO mode (0x80 on, 0x40 off, 0x00 float). */
    private static final int MODES = 0xC04A;
    /** Per output, one word each: 0x80 forward, 0x00 backward. */
    private static final int DIRECTIONS = 0xC050;
    /** Per output, one word each: power 0-7. */
    private static final int POWERS = 0xC056;
    private static final int OUTPUTS = 3;
    private static final int MODE_BITS = 0xC0;
    private static final int MODE_OFF = 0x40;
    private static final int DIRECTION_FORWARD = 0x80;
    private static final int DIRECTION_FLIP = 0x40;
    private static final int SOURCE_CONSTANT = 2;
    private static final int POWER_BITS = 0x07;
    private static final int MODE_ON = 0x80;
    /** The display's backward and forward arrow icons of outputs A, B and C. */
    private static final int[][] ARROWS = {{0x0F, 0x10}, {0x12, 0x13}, {0x15, 0x16}};

    private final RcxCode code;
    private final H8Assembler assembler;

    RcxMotorCommands(RcxCode code) {
        this.code = code;
        this.assembler = code.assembler();
    }

    /** Leaves every output floating, forward, at full power, as LEGO's firmware starts them. */
    void setDefaults() {
        assembler.moveImmediate(DIRECTION_FORWARD, H8Register.R0);
        assembler.moveImmediate(RcxRom.MOTOR_FULL_POWER, H8Register.R1);
        for (int output = 0; output < OUTPUTS; output++) {
            assembler.store(H8Register.R0, DIRECTIONS + 2 * output);
            assembler.store(H8Register.R1, POWERS + 2 * output);
        }
    }

    /** Floats every output, keeping its direction and power. */
    void floatAll() {
        assembler.moveImmediate(0, H8Register.R0);
        for (int output = 0; output < OUTPUTS; output++) {
            assembler.store(H8Register.R0, MODES + 2 * output);
        }
        assembler.call(APPLY);
    }

    /** All the command handlers and the {@link #APPLY} subroutine. */
    void handlers() {
        onOffFloat();
        direction();
        power();
        apply();
    }

    /** Stores the mode bits of the argument for every output its low three bits select. */
    private void onOffFloat() {
        assembler.label(ON_OFF_FLOAT);
        assembler.loadByte(RcxCode.REQUEST + 1, H8ByteRegister.R1L);
        for (int output = 0; output < OUTPUTS; output++) {
            String skip = code.newLabel();
            skipUnlessSelected(output, skip);
            assembler.move(H8ByteRegister.R1L, H8ByteRegister.R2L);
            assembler.and(MODE_BITS, H8ByteRegister.R2L);
            assembler.moveImmediate(0, H8ByteRegister.R2H);
            assembler.store(H8Register.R2, MODES + 2 * output);
            assembler.label(skip);
        }
        assembler.call(APPLY);
        code.replyWith(1);
    }

    /** Sets or flips the direction of every selected output. */
    private void direction() {
        assembler.label(DIRECTION);
        assembler.loadByte(RcxCode.REQUEST + 1, H8ByteRegister.R1L);
        for (int output = 0; output < OUTPUTS; output++) {
            String skip = code.newLabel();
            String set = code.newLabel();
            skipUnlessSelected(output, skip);
            assembler.move(H8ByteRegister.R1L, H8ByteRegister.R2L);
            assembler.and(MODE_BITS, H8ByteRegister.R2L);
            assembler.compareImmediate(DIRECTION_FLIP, H8ByteRegister.R2L);
            assembler.branch(H8Condition.NOT_EQUAL, set);
            assembler.load(DIRECTIONS + 2 * output, H8Register.R3);
            assembler.xor(DIRECTION_FORWARD, H8ByteRegister.R3L);
            assembler.store(H8Register.R3, DIRECTIONS + 2 * output);
            assembler.branch(H8Condition.ALWAYS, skip);
            assembler.label(set);
            assembler.and(DIRECTION_FORWARD, H8ByteRegister.R2L);
            assembler.moveImmediate(0, H8ByteRegister.R2H);
            assembler.store(H8Register.R2, DIRECTIONS + 2 * output);
            assembler.label(skip);
        }
        assembler.call(APPLY);
        code.replyWith(1);
    }

    /** Sets the power of every selected output from a constant; other sources are acknowledged and ignored. */
    private void power() {
        assembler.label(POWER);
        assembler.loadByte(RcxCode.REQUEST + 2, H8ByteRegister.R2L);
        assembler.compareImmediate(SOURCE_CONSTANT, H8ByteRegister.R2L);
        code.jumpIf(H8Condition.NOT_EQUAL, RcxCode.REPLY_ACK);
        assembler.loadByte(RcxCode.REQUEST + 3, H8ByteRegister.R3L);
        assembler.and(POWER_BITS, H8ByteRegister.R3L);
        assembler.moveImmediate(0, H8ByteRegister.R3H);
        assembler.loadByte(RcxCode.REQUEST + 1, H8ByteRegister.R1L);
        for (int output = 0; output < OUTPUTS; output++) {
            String skip = code.newLabel();
            skipUnlessSelected(output, skip);
            assembler.store(H8Register.R3, POWERS + 2 * output);
            assembler.label(skip);
        }
        assembler.call(APPLY);
        code.replyWith(1);
    }

    /**
     * A subroutine that hands every output's state to the ROM: float for mode 0x00, brake for off (0x40), and for on
     * (0x80) forward or backward by its direction, at its power. A {@code mov} sets the condition codes, so each
     * mode is loaded after the ROM mode it falls back to.
     */
    private void apply() {
        assembler.label(APPLY);
        for (int output = 0; output < OUTPUTS; output++) {
            String control = code.newLabel();
            assembler.moveImmediate(RcxRom.MOTOR_FLOAT, H8Register.R1);
            assembler.load(MODES + 2 * output, H8Register.R0);
            assembler.branch(H8Condition.EQUAL, control);
            assembler.moveImmediate(RcxRom.MOTOR_BRAKE, H8Register.R1);
            assembler.compareImmediate(MODE_OFF, H8ByteRegister.R0L);
            assembler.branch(H8Condition.EQUAL, control);
            assembler.moveImmediate(RcxRom.MOTOR_FORWARD, H8Register.R1);
            assembler.load(DIRECTIONS + 2 * output, H8Register.R2);
            assembler.branch(H8Condition.NOT_EQUAL, control);
            assembler.moveImmediate(RcxRom.MOTOR_BACKWARD, H8Register.R1);
            assembler.label(control);
            assembler.load(POWERS + 2 * output, H8Register.R2);
            code.rom().call(RcxRom.CONTROL_MOTOR, Argument.constant(RcxRom.MOTOR_A + output),
                    Argument.register(H8Register.R1), Argument.register(H8Register.R2));
        }
        assembler.returnFromSubroutine();
    }

    /**
     * Lights the arrow of every output that is on, in its direction, and sets {@code icons}' moving word while any
     * is on. Calls no ROM routine, so r3 keeps the moving flag throughout.
     */
    void automaticIcons(RcxDisplayIcons icons) {
        assembler.moveImmediate(0, H8Register.R3);
        for (int output = 0; output < OUTPUTS; output++) {
            String skip = code.newLabel();
            String backward = code.newLabel();
            int backwardIcon = icons.automatic(ARROWS[output][0]);
            int forwardIcon = icons.automatic(ARROWS[output][1]);
            assembler.moveImmediate(0, H8ByteRegister.R2L);
            assembler.storeByte(H8ByteRegister.R2L, backwardIcon);
            assembler.storeByte(H8ByteRegister.R2L, forwardIcon);
            assembler.load(MODES + 2 * output, H8Register.R0);
            assembler.compareImmediate(MODE_ON, H8ByteRegister.R0L);
            assembler.branch(H8Condition.NOT_EQUAL, skip);
            assembler.moveImmediate(1, H8ByteRegister.R3L);
            assembler.moveImmediate(1, H8ByteRegister.R2L);
            assembler.load(DIRECTIONS + 2 * output, H8Register.R0);
            assembler.branch(H8Condition.EQUAL, backward);
            assembler.storeByte(H8ByteRegister.R2L, forwardIcon);
            assembler.branch(H8Condition.ALWAYS, skip);
            assembler.label(backward);
            assembler.storeByte(H8ByteRegister.R2L, backwardIcon);
            assembler.label(skip);
        }
        assembler.store(H8Register.R3, icons.moving());
    }

    /** Branches to {@code skip} unless the output selection in r1l includes {@code output}. */
    private void skipUnlessSelected(int output, String skip) {
        assembler.move(H8ByteRegister.R1L, H8ByteRegister.R2L);
        assembler.and(1 << output, H8ByteRegister.R2L);
        assembler.branch(H8Condition.EQUAL, skip);
    }
}
