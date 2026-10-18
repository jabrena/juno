package io.github.jabrena.juno.backend.h8300;

/**
 * Calls into the RCX's 16 KB boot ROM, whose routines drive the brick's motors, sensors, display, speaker, power and
 * infrared serial port. The ROM calling convention puts the first argument, usually a function opcode such as
 * {@code 0x2000} for motor A, in r6. The remaining 16-bit arguments (a {@code char} too, in the low byte) are pushed
 * last to first so the second argument is on top of the stack, and the caller pops them. A result comes back in r6.
 * The routines may change r0-r6.
 */
public final class RcxRom {
    /** Where the RCX loads firmware; the ROM enters it at the address the download names. */
    public static final int FIRMWARE_START = 0x8000;
    /** The ROM ignores everything from here on when it looks for {@link #UNLOCK_TEXT}. */
    public static final int UNLOCK_LIMIT = 0xCC00;
    /** The text the ROM looks for, starting below {@link #UNLOCK_LIMIT}, before it runs downloaded firmware. */
    public static final String UNLOCK_TEXT = "Do you byte, when I knock?";
    /** The H8/300 reset vector, the ROM's power-up entry. */
    public static final int RESET_VECTOR = 0x0000;
    /** The RAM vector the ROM jumps through after a reset once firmware is loaded. */
    public static final int FIRMWARE_RESET_VECTOR = 0xFD90;
    /** Set to one before entering the ROM reset vector to delete downloaded firmware. */
    public static final int DELETE_FIRMWARE_FLAG = 0xFFCC;

    /** {@code void clear_memory(r0 = from, r1 = to)}: zeroes RAM from {@code from} up to {@code to}. */
    public static final int CLEAR_MEMORY = 0x0436;
    /** {@code void init_timer(counters_t *counters, dispatch_t *dispatch)}; must come before any other init. */
    public static final int INIT_TIMER = 0x3B9A;
    /** {@code void shutdown_timer(void)}: silences the speaker and stops the motors and A/D conversion. */
    public static final int SHUTDOWN_TIMER = 0x3ED4;
    /** {@code void init_power(void)}. */
    public static final int INIT_POWER = 0x2964;
    /** {@code void shutdown_power(void)}: sleeps until the On-Off button is pressed again. */
    public static final int SHUTDOWN_POWER = 0x2A62;
    /** {@code void get_power_status(short code, short *value)}. */
    public static final int GET_POWER_STATUS = 0x29F2;
    /** {@code void init_sensors(void)}: sets up the pins that power the active sensors. */
    public static final int INIT_SENSORS = 0x1498;
    /**
     * {@code void read_sensor(short input, sensor_t *sensor)}: reads the input and updates the struct's raw value,
     * converted value and boolean by its mode. The struct is {@code char type, char mode, short raw, short value,
     * char boolean}; the conversion starts from the previous value, so rotation and edge counts keep counting.
     */
    public static final int READ_SENSOR = 0x14C0;
    /** {@code void set_sensor_active(short input)}: powers a light or rotation sensor. */
    public static final int SET_SENSOR_ACTIVE = 0x1946;
    /** {@code void set_sensor_passive(short input)}: stops powering the input. */
    public static final int SET_SENSOR_PASSIVE = 0x19C4;
    /** {@code void shutdown_sensors(void)}. */
    public static final int SHUTDOWN_SENSORS = 0x1A22;
    /** {@code void control_motor(short motor, short mode, short power)}. */
    public static final int CONTROL_MOTOR = 0x1A4E;
    /** {@code void set_lcd_segment(short icon)}: lights one display icon, {@link #LCD_ICONS} plus its number. */
    public static final int SET_LCD_SEGMENT = 0x1B62;
    /** {@code void clear_lcd_segment(short icon)}: darkens one display icon. */
    public static final int CLEAR_LCD_SEGMENT = 0x1E4A;
    /** {@code void set_lcd_number(short style, short value, short comma)}. */
    public static final int SET_LCD_NUMBER = 0x1FF2;
    /** {@code void clear_display(void)}. */
    public static final int CLEAR_DISPLAY = 0x27AC;
    /** {@code void refresh_display(void)}: sends the display state set since the last refresh to the LCD. */
    public static final int REFRESH_DISPLAY = 0x27C8;
    /** The first of the nine bytes whose bits control the LCD segments directly. */
    public static final int LCD_DISPLAY_MEMORY = 0xEF43;
    /** {@code void play_system_sound(0x4003, short sound)}. */
    public static final int PLAY_SYSTEM_SOUND = 0x299A;
    /** {@code void play_sound_or_set_data_pointer(short code, short param0, short param1)}. */
    public static final int PLAY_SOUND_OR_SET_DATA_POINTER = 0x327C;
    /** {@code void get_sound_playing_flag(0x700c, char *playing)}. */
    public static final int GET_SOUND_PLAYING_FLAG = 0x3CCC;
    /**
     * {@code void init_serial(char *received, char *counter, short headers, short complements)}: starts the ROM's IR
     * message receiver, which checks each LEGO message's header, complements and checksum.
     */
    public static final int INIT_SERIAL = 0x30D0;
    /** {@code void check_for_data(char *valid, char **next)}: {@code *valid} becomes 1 once a message arrived. */
    public static final int CHECK_FOR_DATA = 0x3426;
    /**
     * {@code void receive_data(void *data, char capacity, char *length)}: copies the message (opcode, arguments,
     * checksum) and adds its length to {@code *length}.
     */
    public static final int RECEIVE_DATA = 0x33B0;
    /** {@code char send_data(0x1775, short unused, void *data, short length)}: 0 once sent, 0x4C while busy. */
    public static final int SEND_DATA = 0x343E;
    /** {@code void shutdown_serial(void)}. */
    public static final int SHUTDOWN_SERIAL = 0x3636;

    /** {@link #GET_POWER_STATUS}'s code for the On-Off button: 0 while held down. */
    public static final int POWER_STATUS_ON_OFF = 0x4000;
    /** {@link #GET_POWER_STATUS}'s code for the battery: A/D units, times 43988 / 1560 for millivolts. */
    public static final int POWER_STATUS_BATTERY = 0x4001;
    /** {@link #PLAY_SYSTEM_SOUND}'s opcode. */
    public static final int SOUND_PLAY = 0x4003;
    /** {@link #PLAY_SOUND_OR_SET_DATA_POINTER}'s code for a queued tone: frequency in Hz, then duration. */
    public static final int SOUND_TONE = 0x1773;
    /** {@link #GET_SOUND_PLAYING_FLAG}'s opcode. */
    public static final int SOUND_PLAYING = 0x700C;
    /** {@link #SEND_DATA}'s code for a message built from the data, with header and complements. */
    public static final int SEND_SHORT = 0x1775;
    /** The sensor routines' opcode for input 1; inputs 2 and 3 follow. */
    public static final int SENSOR_1 = 0x1000;
    /** {@link #CONTROL_MOTOR}'s opcodes for the three outputs. */
    public static final int MOTOR_A = 0x2000;
    public static final int MOTOR_B = 0x2001;
    public static final int MOTOR_C = 0x2002;
    public static final int MOTOR_FORWARD = 1;
    public static final int MOTOR_BACKWARD = 2;
    public static final int MOTOR_BRAKE = 3;
    public static final int MOTOR_FLOAT = 4;
    /** The full {@link #CONTROL_MOTOR} power, a 100% duty cycle. */
    public static final int MOTOR_FULL_POWER = 7;
    /**
     * The display icons' opcodes are this plus 0x06 standing figure, 0x07 walking figure, 0x08-0x0D sensor 1-3 view and
     * pressed markers, 0x0E-0x16 motor A-C view, backward and forward arrows, 0x18 datalog, 0x19 download, 0x1A upload,
     * 0x1B battery low, 0x1C short range, 0x1D long range, and 0x20 every segment.
     */
    public static final int LCD_ICONS = 0x3000;
    /** {@link #SET_LCD_NUMBER}'s style for a signed decimal value, -9999 to 9999. */
    public static final int LCD_SIGNED = 0x3001;
    /** {@link #SET_LCD_NUMBER}'s style for an unsigned decimal value. */
    public static final int LCD_UNSIGNED = 0x301F;
    /** {@link #SET_LCD_NUMBER}'s comma code for no decimal point. */
    public static final int LCD_NO_COMMA = 0x3002;

    private final H8Assembler assembler;

    public RcxRom(H8Assembler assembler) {
        this.assembler = assembler;
    }

    /** A ROM routine argument: a constant or a register's value. */
    public sealed interface Argument {
        /** A 16-bit constant. */
        record Constant(int value) implements Argument {
        }

        /** The value of a register other than r6, which carries the first argument. */
        record InRegister(H8Register register) implements Argument {
        }

        static Argument constant(int value) {
            return new Constant(value);
        }

        static Argument register(H8Register register) {
            return new InRegister(register);
        }
    }

    /** Calls the ROM routine at {@code routine} with {@code first} in r6 and the other arguments on the stack. */
    public void call(int routine, int first, int... stacked) {
        Argument[] arguments = new Argument[stacked.length];
        for (int i = 0; i < stacked.length; i++) {
            arguments[i] = Argument.constant(stacked[i]);
        }
        call(routine, Argument.constant(first), arguments);
    }

    /**
     * Calls the ROM routine at {@code routine} with {@code first} in r6 and the other arguments on the stack. A
     * constant goes through r6 on its way to the stack, so no stacked argument may be r6 itself.
     */
    public void call(int routine, Argument first, Argument... stacked) {
        for (int i = stacked.length - 1; i >= 0; i--) {
            switch (stacked[i]) {
                case Argument.Constant(int value) -> {
                    assembler.moveImmediate(value, H8Register.R6);
                    assembler.push(H8Register.R6);
                }
                case Argument.InRegister(H8Register register) -> {
                    if (register == H8Register.R6) {
                        throw new FirmwareGenerationException(
                                "r6 carries the first ROM argument; pass it in another register");
                    }
                    assembler.push(register);
                }
            }
        }
        switch (first) {
            case Argument.Constant(int value) -> assembler.moveImmediate(value, H8Register.R6);
            case Argument.InRegister(H8Register register) -> assembler.move(register, H8Register.R6);
        }
        assembler.call(routine);
        for (int i = 0; i < stacked.length; i++) {
            assembler.adds(2, H8Register.SP);
        }
    }

    /** Calls a ROM routine that takes no arguments. */
    public void call(int routine) {
        assembler.call(routine);
    }
}
