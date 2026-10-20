package io.github.jabrena.juno.backend.h8300;

import io.github.jabrena.juno.backend.h8300.RcxRom.Argument;

/**
 * The input commands of {@link RcxRemoteFirmware}: set type (0x32), set mode (0x42), clear (0xD1) and poll (0x12)
 * for inputs 0-2 (polling the message is left to {@link RcxRemoteControl}, and the brick's clicked buttons to
 * {@link RcxBrickButtons}). Each input has a ROM {@code sensor_t}
 * that {@code read_sensor} converts by its mode, read when a poll asks for it and whenever the ROM's A/D handler flags
 * fresh readings, so rotation and edge counts keep up.
 * Setting a type picks LEGO's default mode for it and powers light and rotation sensors.
 */
final class RcxSensorCommands {
    static final String SET_TYPE = "set_input_type";
    static final String SET_MODE = "set_input_mode";
    static final String CLEAR = "clear_sensor";
    static final String POLL = "poll";
    /** Subroutine: reads all three inputs. */
    static final String UPDATE_ALL = "update_sensors";
    /** Subroutine: powers or unpowers all three inputs by their types, after a wake-up. */
    static final String POWER_ALL = "power_sensors";

    /** Three {@code sensor_t} structs, one per input. */
    private static final int SENSORS = 0xC080;
    private static final int SENSOR_SIZE = 8;
    private static final int INPUTS = 3;
    private static final int TYPE = 0;
    private static final int MODE = 1;
    private static final int RAW = 2;
    private static final int VALUE = 4;
    private static final int BOOLEAN = 6;
    private static final int TYPE_TOUCH = 1;
    private static final int TYPE_LIGHT = 3;
    /** The display's pressed-marker icons of inputs 1, 2 and 3. */
    private static final int[] PRESSED_ICONS = {0x09, 0x0B, 0x0D};
    private static final int TYPE_ROTATION = 4;
    /** LEGO's default mode per type: raw, touch boolean, temperature in °C, light percent, rotation angle. */
    private static final int[] DEFAULT_MODES = {0x00, 0x20, 0xA0, 0x80, 0xE0};
    private static final int SOURCE_VALUE = 9;
    private static final int SOURCE_TYPE = 10;
    private static final int SOURCE_MODE = 11;
    private static final int SOURCE_RAW = 12;
    private static final int SOURCE_BOOLEAN = 13;
    /** The last message, which {@link RcxRemoteControl} keeps. */
    private static final int SOURCE_MESSAGE = 15;

    private static final String UPDATE_ONE = "update_sensor";
    private static final String POWER_ONE = "power_sensor";
    private static final String MODES_TABLE = "sensor_modes";
    private static final String POLL_ZERO = "poll_zero";

    private final RcxCode code;
    private final H8Assembler assembler;

    RcxSensorCommands(RcxCode code) {
        this.code = code;
        this.assembler = code.assembler();
    }

    /** All the command handlers, the subroutines and the default-mode table. */
    void handlers() {
        setType();
        setMode();
        clear();
        poll();
        everyInput(UPDATE_ALL, UPDATE_ONE);
        everyInput(POWER_ALL, POWER_ONE);
        updateOne();
        powerOne();
        assembler.label(MODES_TABLE);
        assembler.data(DEFAULT_MODES);
    }

    /** Lights the pressed marker of every touch sensor whose last reading was pressed. */
    void automaticIcons(RcxDisplayIcons icons) {
        for (int input = 0; input < INPUTS; input++) {
            String store = code.newLabel();
            int sensor = SENSORS + SENSOR_SIZE * input;
            assembler.moveImmediate(0, H8ByteRegister.R2L);
            assembler.loadByte(sensor + TYPE, H8ByteRegister.R0L);
            assembler.compareImmediate(TYPE_TOUCH, H8ByteRegister.R0L);
            assembler.branch(H8Condition.NOT_EQUAL, store);
            assembler.loadByte(sensor + BOOLEAN, H8ByteRegister.R2L);
            assembler.label(store);
            assembler.storeByte(H8ByteRegister.R2L, icons.automatic(PRESSED_ICONS[input]));
        }
    }

    private void setType() {
        assembler.label(SET_TYPE);
        selectInput(1, RcxCode.REPLY_ACK);
        assembler.loadByte(RcxCode.REQUEST + 2, H8ByteRegister.R0L);
        assembler.moveImmediate(0, H8ByteRegister.R0H);
        assembler.compareImmediate(DEFAULT_MODES.length, H8ByteRegister.R0L);
        code.jumpIf(H8Condition.CARRY_CLEAR, RcxCode.REPLY_ACK);
        assembler.storeByteIndexed(H8ByteRegister.R0L, TYPE, H8Register.R4);
        assembler.loadByteIndexed(MODES_TABLE, H8Register.R0, H8ByteRegister.R1L);
        assembler.storeByteIndexed(H8ByteRegister.R1L, MODE, H8Register.R4);
        clearReading();
        assembler.call(POWER_ONE);
        code.replyWith(1);
    }

    private void setMode() {
        assembler.label(SET_MODE);
        selectInput(1, RcxCode.REPLY_ACK);
        assembler.loadByte(RcxCode.REQUEST + 2, H8ByteRegister.R0L);
        assembler.storeByteIndexed(H8ByteRegister.R0L, MODE, H8Register.R4);
        clearReading();
        code.replyWith(1);
    }

    private void clear() {
        assembler.label(CLEAR);
        selectInput(1, RcxCode.REPLY_ACK);
        clearReading();
        code.replyWith(1);
    }

    /** Replies with the input's value, raw reading, boolean, type or mode, or the message; 0 for any other source. */
    private void poll() {
        assembler.label(POLL);
        assembler.loadByte(RcxCode.REQUEST + 1, H8ByteRegister.R0L);
        assembler.compareImmediate(SOURCE_MESSAGE, H8ByteRegister.R0L);
        code.jumpIf(H8Condition.EQUAL, RcxRemoteControl.POLL_MESSAGE);
        assembler.compareImmediate(RcxBrickButtons.SOURCE_CLICKED, H8ByteRegister.R0L);
        code.jumpIf(H8Condition.EQUAL, RcxBrickButtons.POLL_CLICKED);
        assembler.compareImmediate(SOURCE_VALUE, H8ByteRegister.R0L);
        code.jumpIf(H8Condition.CARRY_SET, POLL_ZERO);
        assembler.compareImmediate(SOURCE_BOOLEAN + 1, H8ByteRegister.R0L);
        code.jumpIf(H8Condition.CARRY_CLEAR, POLL_ZERO);
        selectInput(2, POLL_ZERO);
        assembler.call(UPDATE_ONE);
        selectInput(2, POLL_ZERO);
        assembler.loadByte(RcxCode.REQUEST + 1, H8ByteRegister.R0L);
        assembler.moveImmediate(0, H8Register.R1);
        String reply = code.newLabel();
        pollSource(SOURCE_VALUE, reply, () -> assembler.loadIndexed(VALUE, H8Register.R4, H8Register.R1));
        pollSource(SOURCE_RAW, reply, () -> assembler.loadIndexed(RAW, H8Register.R4, H8Register.R1));
        pollSource(SOURCE_BOOLEAN, reply,
                () -> assembler.loadByteIndexed(BOOLEAN, H8Register.R4, H8ByteRegister.R1L));
        pollSource(SOURCE_TYPE, reply, () -> assembler.loadByteIndexed(TYPE, H8Register.R4, H8ByteRegister.R1L));
        pollSource(SOURCE_MODE, reply, () -> assembler.loadByteIndexed(MODE, H8Register.R4, H8ByteRegister.R1L));
        assembler.label(reply);
        code.replyWithWord(H8Register.R1);
        assembler.label(POLL_ZERO);
        assembler.moveImmediate(0, H8Register.R1);
        code.replyWithWord(H8Register.R1);
    }

    /** If r0l holds {@code source}, loads its value into r1 and branches to {@code reply}. */
    private void pollSource(int source, String reply, Runnable load) {
        String next = code.newLabel();
        assembler.compareImmediate(source, H8ByteRegister.R0L);
        assembler.branch(H8Condition.NOT_EQUAL, next);
        load.run();
        assembler.branch(H8Condition.ALWAYS, reply);
        assembler.label(next);
    }

    /** A subroutine calling {@code each} with r2 = input and r4 = its struct, for every input. */
    private void everyInput(String name, String each) {
        assembler.label(name);
        for (int input = 0; input < INPUTS; input++) {
            assembler.moveImmediate(input, H8Register.R2);
            assembler.moveImmediate(SENSORS + SENSOR_SIZE * input, H8Register.R4);
            assembler.call(each);
        }
        assembler.returnFromSubroutine();
    }

    /** Subroutine: {@code read_sensor} for input r2 into its struct at r4. */
    private void updateOne() {
        assembler.label(UPDATE_ONE);
        inputOpcode();
        code.rom().call(RcxRom.READ_SENSOR, Argument.register(H8Register.R5), Argument.register(H8Register.R4));
        assembler.returnFromSubroutine();
    }

    /** Subroutine: powers input r2 if its type at r4 is light or rotation, and stops powering it otherwise. */
    private void powerOne() {
        String active = code.newLabel();
        assembler.label(POWER_ONE);
        inputOpcode();
        assembler.loadByteIndexed(TYPE, H8Register.R4, H8ByteRegister.R0L);
        assembler.compareImmediate(TYPE_LIGHT, H8ByteRegister.R0L);
        assembler.branch(H8Condition.EQUAL, active);
        assembler.compareImmediate(TYPE_ROTATION, H8ByteRegister.R0L);
        assembler.branch(H8Condition.EQUAL, active);
        code.rom().call(RcxRom.SET_SENSOR_PASSIVE, Argument.register(H8Register.R5));
        assembler.returnFromSubroutine();
        assembler.label(active);
        code.rom().call(RcxRom.SET_SENSOR_ACTIVE, Argument.register(H8Register.R5));
        assembler.returnFromSubroutine();
    }

    /**
     * Loads the input number at {@code REQUEST + offset} into r2 and its struct's address into r4, going to
     * {@code outOfRange} for an input above 2.
     */
    private void selectInput(int offset, String outOfRange) {
        assembler.loadByte(RcxCode.REQUEST + offset, H8ByteRegister.R2L);
        assembler.moveImmediate(0, H8ByteRegister.R2H);
        assembler.compareImmediate(INPUTS, H8ByteRegister.R2L);
        code.jumpIf(H8Condition.CARRY_CLEAR, outOfRange);
        assembler.move(H8Register.R2, H8Register.R4);
        for (int shift = 0; shift < Integer.numberOfTrailingZeros(SENSOR_SIZE); shift++) {
            assembler.add(H8Register.R4, H8Register.R4);
        }
        assembler.moveImmediate(SENSORS, H8Register.R3);
        assembler.add(H8Register.R3, H8Register.R4);
    }

    /** r5 = the ROM opcode of input r2. */
    private void inputOpcode() {
        assembler.moveImmediate(RcxRom.SENSOR_1, H8Register.R5);
        assembler.add(H8Register.R2, H8Register.R5);
    }

    /** Zeroes the value and boolean of the struct at r4, restarting counts. */
    private void clearReading() {
        assembler.moveImmediate(0, H8Register.R1);
        assembler.storeIndexed(H8Register.R1, VALUE, H8Register.R4);
        assembler.storeByteIndexed(H8ByteRegister.R1L, BOOLEAN, H8Register.R4);
    }
}
