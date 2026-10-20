package io.github.jabrena.juno.backend.h8300;

import io.github.jabrena.juno.backend.h8300.RcxRom.Argument;

/**
 * The display icons of {@link RcxRemoteFirmware}. Each icon, numbered as the ROM numbers it ({@link RcxRom#LCD_ICONS}
 * plus 0x06-0x20), is lit when the Arduino set it with command 0xB2 or when the brick's own state calls for it: the
 * motor arrows of running outputs (from {@link RcxMotorCommands}), the pressed marker of touch sensors (from
 * {@link RcxSensorCommands}), the battery-low warning below about 6.5 V, and the minifigure, which stands, and walks
 * while any motor runs, and the IR cones after each valid message: the short-range cone, then both, then none, a
 * tick (0.3 s) each. While text shows ({@link RcxDisplayText}), only the cones are lit. {@link #UPDATE} recomputes and redraws them all.
 */
final class RcxDisplayIcons {
    /** Command 0xB2: icon 0x06-0x20, then 1 to light it or 0 to leave it to the brick's own state. */
    static final String SET_ICON = "set_icon";
    /** Subroutine: recomputes the brick's own icons and redraws every icon. */
    static final String UPDATE = "update_icons";
    /** Subroutine: advances the walking animation, then {@link #UPDATE}. */
    static final String TICK = "icons_tick";

    /** One byte per icon number 0x00-0x20, non-zero while the Arduino has the icon lit. */
    private static final int MANUAL = 0xC0A0;
    /** One byte per icon number 0x00-0x20, non-zero while the brick's own state lights the icon. */
    private static final int AUTOMATIC = 0xC0D0;
    /** The position in {@link #DRAWN} while redrawing. */
    private static final int CURSOR = 0xC0F2;
    /** Alternates 0 and 1 every tick: the walking figure shows on 1 while a motor runs. */
    private static final int PHASE = 0xC0F4;
    /** Non-zero while any output is on; written by {@link RcxMotorCommands#automaticIcons}. */
    private static final int MOVING = 0xC0F6;
    private static final int BATTERY = 0xC0F8;
    /**
     * The IR cones' animation after a valid message: 2 shows the short-range cone, 1 both cones, 0 none; each tick
     * counts it down.
     */
    private static final int RECEIVING = 0xC0FA;
    private static final int RECEIVING_STEPS = 2;
    private static final int SHORT_RANGE = 0x1C;
    private static final int LONG_RANGE = 0x1D;
    private static final int FIRST_ICON = 0x06;
    private static final int ALL_SEGMENTS = 0x20;
    private static final int STANDING = 0x06;
    private static final int WALKING = 0x07;
    private static final int BATTERY_LOW = 0x1B;
    /** The ROM's battery reading for 6.5 V: millivolts x 1560 / 43988. */
    private static final int BATTERY_LOW_READING = 231;
    /** Every icon the ROM draws one by one; 0x17, 0x1E and 0x1F are number styles, not icons. */
    private static final int[] DRAWN = {
        0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10, 0x11, 0x12, 0x13, 0x14, 0x15, 0x16,
        0x18, 0x19, 0x1A, 0x1B, 0x1C, 0x1D};
    private static final String DRAWN_TABLE = "drawn_icons";

    private final RcxCode code;
    private final H8Assembler assembler;

    RcxDisplayIcons(RcxCode code) {
        this.code = code;
        this.assembler = code.assembler();
    }

    /** The byte that lights icon {@code icon} from the brick's own state. */
    int automatic(int icon) {
        return AUTOMATIC + icon;
    }

    /** Starts the IR cones' animation: called for every valid message the ROM receives. Changes r6. */
    void signalReceived() {
        assembler.moveImmediate(RECEIVING_STEPS, H8Register.R6);
        assembler.store(H8Register.R6, RECEIVING);
    }

    /** The word {@link RcxMotorCommands} sets while any output is on. */
    int moving() {
        return MOVING;
    }

    /** The command handler, the subroutines and the table of drawn icons. */
    void handlers(RcxMotorCommands motors, RcxSensorCommands sensors, RcxDisplayText text) {
        setIcon();
        tick();
        update(motors, sensors, text);
        assembler.label(DRAWN_TABLE);
        assembler.data(DRAWN);
    }

    /** Stores the Arduino's choice for the icon; the reply redraws the display. */
    private void setIcon() {
        assembler.label(SET_ICON);
        assembler.loadByte(RcxCode.REQUEST + 1, H8ByteRegister.R2L);
        assembler.moveImmediate(0, H8ByteRegister.R2H);
        assembler.compareImmediate(FIRST_ICON, H8ByteRegister.R2L);
        code.jumpIf(H8Condition.CARRY_SET, RcxCode.REPLY_ACK);
        assembler.compareImmediate(ALL_SEGMENTS + 1, H8ByteRegister.R2L);
        code.jumpIf(H8Condition.CARRY_CLEAR, RcxCode.REPLY_ACK);
        assembler.loadByte(RcxCode.REQUEST + 2, H8ByteRegister.R0L);
        assembler.storeByteIndexed(H8ByteRegister.R0L, MANUAL, H8Register.R2);
        code.replyWith(1);
    }

    private void tick() {
        String still = code.newLabel();
        assembler.label(TICK);
        assembler.load(PHASE, H8Register.R0);
        assembler.xor(1, H8ByteRegister.R0L);
        assembler.store(H8Register.R0, PHASE);
        assembler.load(RECEIVING, H8Register.R0);
        assembler.branch(H8Condition.EQUAL, still);
        assembler.subs(1, H8Register.R0);
        assembler.store(H8Register.R0, RECEIVING);
        assembler.label(still);
        assembler.call(UPDATE);
        assembler.returnFromSubroutine();
    }

    private void update(RcxMotorCommands motors, RcxSensorCommands sensors, RcxDisplayText text) {
        assembler.label(UPDATE);
        motors.automaticIcons(this);
        sensors.automaticIcons(this);
        batteryIcon();
        figureIcons();
        coneIcons();
        redraw(text);
        code.rom().call(RcxRom.REFRESH_DISPLAY);
        assembler.returnFromSubroutine();
    }

    private void batteryIcon() {
        String store = code.newLabel();
        code.rom().call(RcxRom.GET_POWER_STATUS, RcxRom.POWER_STATUS_BATTERY, BATTERY);
        assembler.moveImmediate(0, H8ByteRegister.R2L);
        assembler.moveImmediate(BATTERY_LOW_READING, H8Register.R1);
        assembler.load(BATTERY, H8Register.R0);
        assembler.compare(H8Register.R1, H8Register.R0);
        assembler.branch(H8Condition.CARRY_CLEAR, store);
        assembler.moveImmediate(1, H8ByteRegister.R2L);
        assembler.label(store);
        assembler.storeByte(H8ByteRegister.R2L, automatic(BATTERY_LOW));
    }

    /** The short-range cone while the animation runs, and the long-range one too on its second step. */
    private void coneIcons() {
        String store = code.newLabel();
        assembler.moveImmediate(0, H8ByteRegister.R2L);
        assembler.moveImmediate(0, H8ByteRegister.R3L);
        assembler.load(RECEIVING, H8Register.R0);
        assembler.branch(H8Condition.EQUAL, store);
        assembler.moveImmediate(1, H8ByteRegister.R2L);
        assembler.compareImmediate(RECEIVING_STEPS, H8ByteRegister.R0L);
        assembler.branch(H8Condition.EQUAL, store);
        assembler.moveImmediate(1, H8ByteRegister.R3L);
        assembler.label(store);
        assembler.storeByte(H8ByteRegister.R2L, automatic(SHORT_RANGE));
        assembler.storeByte(H8ByteRegister.R3L, automatic(LONG_RANGE));
    }

    /** Standing, or walking on every other tick while any motor runs. */
    private void figureIcons() {
        String store = code.newLabel();
        assembler.moveImmediate(1, H8ByteRegister.R2L);
        assembler.moveImmediate(0, H8ByteRegister.R3L);
        assembler.load(MOVING, H8Register.R0);
        assembler.branch(H8Condition.EQUAL, store);
        assembler.load(PHASE, H8Register.R0);
        assembler.branch(H8Condition.EQUAL, store);
        assembler.moveImmediate(0, H8ByteRegister.R2L);
        assembler.moveImmediate(1, H8ByteRegister.R3L);
        assembler.label(store);
        assembler.storeByte(H8ByteRegister.R2L, automatic(STANDING));
        assembler.storeByte(H8ByteRegister.R3L, automatic(WALKING));
    }

    /**
     * Redraws the whole display: clears it, draws the number ({@link RcxCode#DRAW_NUMBER}), lights every icon in
     * {@link #DRAWN} that should be on, then every segment if the Arduino asked for that. The ROM's
     * {@code clear_lcd_segment} is never used: on the brick, clearing icons one by one and lighting them again every
     * 0.3 s left only the standing figure, while a cleared display followed by the icons shows them all.
     * The position lives in memory since the ROM calls change the registers.
     */
    private void redraw(RcxDisplayText text) {
        String loop = code.newLabel();
        String lit = code.newLabel();
        String next = code.newLabel();
        String done = code.newLabel();
        String cones = code.newLabel();
        code.rom().call(RcxRom.CLEAR_DISPLAY);
        assembler.call(RcxCode.DRAW_NUMBER);
        // Text shows alone but for the IR cones.
        text.jumpIfTextShown(cones);
        code.clearWord(CURSOR);
        assembler.label(loop);
        assembler.load(CURSOR, H8Register.R4);
        assembler.loadByteIndexed(DRAWN_TABLE, H8Register.R4, H8ByteRegister.R2L);
        assembler.moveImmediate(0, H8ByteRegister.R2H);
        iconOpcode();
        assembler.loadByteIndexed(MANUAL, H8Register.R2, H8ByteRegister.R0L);
        assembler.branch(H8Condition.NOT_EQUAL, lit);
        assembler.loadByteIndexed(AUTOMATIC, H8Register.R2, H8ByteRegister.R0L);
        assembler.branch(H8Condition.EQUAL, next);
        assembler.label(lit);
        code.rom().call(RcxRom.SET_LCD_SEGMENT, Argument.register(H8Register.R5));
        assembler.label(next);
        assembler.load(CURSOR, H8Register.R0);
        assembler.adds(1, H8Register.R0);
        assembler.store(H8Register.R0, CURSOR);
        assembler.moveImmediate(DRAWN.length, H8Register.R1);
        assembler.compare(H8Register.R1, H8Register.R0);
        assembler.branch(H8Condition.NOT_EQUAL, loop);
        assembler.loadByte(MANUAL + ALL_SEGMENTS, H8ByteRegister.R0L);
        assembler.branch(H8Condition.EQUAL, done);
        code.rom().call(RcxRom.SET_LCD_SEGMENT, RcxRom.LCD_ICONS + ALL_SEGMENTS);
        assembler.branch(H8Condition.ALWAYS, done);
        assembler.label(cones);
        lightIfAutomatic(SHORT_RANGE);
        lightIfAutomatic(LONG_RANGE);
        assembler.label(done);
    }

    /** Lights {@code icon} if the brick's own state calls for it. */
    private void lightIfAutomatic(int icon) {
        String dark = code.newLabel();
        assembler.loadByte(automatic(icon), H8ByteRegister.R0L);
        assembler.branch(H8Condition.EQUAL, dark);
        code.rom().call(RcxRom.SET_LCD_SEGMENT, RcxRom.LCD_ICONS + icon);
        assembler.label(dark);
    }

    /** r5 = the ROM opcode of icon r2. */
    private void iconOpcode() {
        assembler.moveImmediate(RcxRom.LCD_ICONS, H8Register.R5);
        assembler.add(H8Register.R2, H8Register.R5);
    }
}
