package io.github.jabrena.juno.hardware.device.lcd;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.internal.NativeSdCard;
import io.github.jabrena.juno.util.Delay;
import org.jspecify.annotations.Nullable;

/**
 * Driver for the common "LCD Keypad Shield" (a 16x2 HD44780-compatible character LCD in 4-bit
 * mode, plus 5 buttons wired through a single resistor ladder into one analog pin), wired for its
 * standard pinout as used by e.g. <a href="https://github.com/dzindra/lcdkeypad">dzindra/lcdkeypad</a>:
 * LCD data/control on digital pins 4-9, backlight on digital pin 10, buttons on {@code A0}.
 *
 * <p>Unlike {@link io.github.jabrena.juno.hardware.led.LedMatrix}, this shield needs no new compiler
 * intrinsic: every operation here is built from {@link Gpio#pinMode}, {@link Gpio#digitalWrite},
 * {@link Gpio#analogRead}, and {@link Delay}, the same way {@link io.github.jabrena.juno.hardware.led.LedCanvas}
 * is built from {@link io.github.jabrena.juno.hardware.led.LedMatrix}.
 *
 * <p>It cannot be stacked with the Arduino WiFi Shield on a UNO R3: both shields are wired to D4, D7 and D10 (this
 * shield's LCD data and backlight, that one's SD-card chip select, handshake line and Wi-Fi chip select), so the
 * compiler rejects a UNO R3 program that uses this shield together with {@code Wifi}, {@code Udp}, {@code Tcp},
 * {@code HttpClient} or {@code LocalDateTime.now()}.
 */
public final class LcdKeypadShield {
    /** No button is currently pressed. */
    public static final int NONE = 0;
    public static final int RIGHT = 1;
    public static final int UP = 2;
    public static final int DOWN = 3;
    public static final int LEFT = 4;
    public static final int SELECT = 5;

    /** Character columns per row. */
    public static final int COLUMNS = 16;
    /** Character rows. */
    public static final int ROWS = 2;

    private static final int PIN_RS = 8;
    private static final int PIN_ENABLE = 9;
    private static final int PIN_D4 = 4;
    private static final int PIN_D5 = 5;
    private static final int PIN_D6 = 6;
    private static final int PIN_D7 = 7;
    private static final int PIN_BACKLIGHT = 10;
    /** The chip select of the SD card stacked under this shield, the same pin as the backlight. */
    private static final int PIN_SD_CHIP_SELECT = 10;
    private static final int PIN_BUTTONS = 14; // A0

    // Analog reading upper bounds for each button, ascending along the shield's resistor ladder.
    private static final int RIGHT_THRESHOLD = 50;
    private static final int UP_THRESHOLD = 195;
    private static final int DOWN_THRESHOLD = 380;
    private static final int LEFT_THRESHOLD = 555;
    private static final int SELECT_THRESHOLD = 790;

    // HD44780 instructions and the flags this driver sets on them.
    private static final int CLEAR_DISPLAY = 0x01;
    private static final int RETURN_HOME = 0x02;
    private static final int ENTRY_MODE_SET = 0x04;
    private static final int ENTRY_LEFT = 0x02;
    private static final int DISPLAY_CONTROL = 0x08;
    private static final int DISPLAY_ON = 0x04;
    private static final int FUNCTION_SET = 0x20;
    private static final int FOUR_BIT_MODE = 0x00;
    private static final int TWO_LINE = 0x08;
    private static final int DOTS_5X8 = 0x00;
    private static final int SET_DDRAM_ADDRESS = 0x80;
    private static final int ROW_1_OFFSET = 0x40;

    // The HD44780 datasheet's minimums (enable pulse >450ns, command settle >37us) leave very
    // little margin on real clone hardware; these are deliberately generous (updates here only
    // happen a few times a second, so the extra time costs nothing observable) to stay reliable
    // under voltage sag from other onboard activity (e.g. WiFi radio bursts on the UNO R4 WiFi).
    private static final int ENABLE_PULSE_MICROS = 20;
    private static final int NIBBLE_SETTLE_MICROS = 200;
    private static final int COMMAND_SETTLE_MICROS = 200;

    private final Gpio gpio;
    private final int pinRs = PIN_RS;
    private final int pinEnable = PIN_ENABLE;
    private final int pinD4 = PIN_D4;
    private final int pinD5 = PIN_D5;
    private final int pinD6 = PIN_D6;
    private final int pinD7 = PIN_D7;
    private final int pinBacklight = PIN_BACKLIGHT;
    private final int pinButtons = PIN_BUTTONS;
    private @Nullable SdCard sdCard;

    /** A shield on the board's own pins. Create it once, at startup, and share it. */
    public LcdKeypadShield() {
        this(ArduinoUno.getGpio());
    }

    /** A shield driven through {@code gpio}: a mocked one in a unit test. */
    public LcdKeypadShield(Gpio gpio) {
        this.gpio = gpio;
    }

    /**
     * The SD card read through this shield: the shield has no socket of its own, so it is the card of the
     * {@link io.github.jabrena.juno.hardware.device.sd.DataLoggerShield} stacked under it, selected on D10. D10 is
     * also this shield's backlight line: isolate or reroute it before stacking, and do not call
     * {@link #backlight(boolean)} once the card is initialized. The same instance every call.
     */
    public SdCard getSdCard() {
        SdCard card = sdCard;
        if (card == null) {
            card = NativeSdCard.sdCard(PIN_SD_CHIP_SELECT);
            sdCard = card;
        }
        return card;
    }

    /** Configures the shield's pins, runs the HD44780 4-bit init sequence, and turns the backlight on. */
    public void begin() {
        gpio.pinMode(pinRs, Gpio.getOutput());
        gpio.pinMode(pinEnable, Gpio.getOutput());
        gpio.pinMode(pinD4, Gpio.getOutput());
        gpio.pinMode(pinD5, Gpio.getOutput());
        gpio.pinMode(pinD6, Gpio.getOutput());
        gpio.pinMode(pinD7, Gpio.getOutput());
        gpio.pinMode(pinButtons, Gpio.getInput());
        gpio.digitalWrite(pinRs, false);
        gpio.digitalWrite(pinEnable, false);
        backlight(true);

        // HD44780 datasheet fig. 24: force 8-bit mode three times, then switch to 4-bit mode.
        Delay.millis(50);
        writeNibble(0x03);
        Delay.micros(4500);
        writeNibble(0x03);
        Delay.micros(4500);
        writeNibble(0x03);
        Delay.micros(150);
        writeNibble(0x02);

        command(FUNCTION_SET | FOUR_BIT_MODE | TWO_LINE | DOTS_5X8);
        command(DISPLAY_CONTROL | DISPLAY_ON);
        clear();
        command(ENTRY_MODE_SET | ENTRY_LEFT);
    }

    /** Clears the display and returns the cursor to (0, 0). */
    public void clear() {
        command(CLEAR_DISPLAY);
        Delay.millis(2);
    }

    /** Returns the cursor to (0, 0) without clearing the display. */
    public void home() {
        command(RETURN_HOME);
        Delay.millis(2);
    }

    /** Moves the cursor to {@code column} (0-15) on {@code row} (0-1). */
    public void setCursor(int column, int row) {
        int rowOffset = 0;
        if (row == 1) {
            rowOffset = ROW_1_OFFSET;
        }
        command(SET_DDRAM_ADDRESS | (rowOffset + column));
    }

    /**
     * Turns the backlight on or off. The shield's backlight LED is wired straight to this pin with
     * no current-limiting resistor on some board revisions, so this never drives the pin HIGH: "on"
     * writes LOW and then floats the pin ({@code INPUT}), "off" drives it LOW as an output. Driving
     * the pin HIGH can pull down the shared 5V rail hard enough to keep the LCD itself from
     * initializing.
     */
    public void backlight(boolean on) {
        gpio.digitalWrite(pinBacklight, false);
        if (on) {
            gpio.pinMode(pinBacklight, Gpio.getInput());
        } else {
            gpio.pinMode(pinBacklight, Gpio.getOutput());
        }
    }

    /** Prints {@code text} starting at the cursor, without wrapping. */
    public void print(String text) {
        int length = text.length();
        for (int i = 0; i < length; i++) {
            writeData(text.charAt(i));
        }
    }

    /** Prints {@code value} in decimal, starting at the cursor. */
    public void print(int value) {
        print(String.valueOf(value));
    }

    /**
     * Prints {@code value} in decimal, left-padded with zeros to at least {@code width} digits (so {@code print(7, 2)}
     * shows {@code 07}), starting at the cursor. Unlike {@link #print(int)}, this never builds a runtime
     * {@code String}, so a display redrawn in a loop allocates nothing. A negative value gets a leading {@code -}.
     */
    public void print(int value, int width) {
        if (value == Integer.MIN_VALUE) {
            print("-2147483648");
            return;
        }
        int magnitude = value;
        if (magnitude < 0) {
            writeData('-');
            magnitude = -magnitude;
        }
        int divisor = 1;
        int digits = 1;
        while (divisor <= magnitude / 10) {
            divisor = divisor * 10;
            digits = digits + 1;
        }
        for (int pad = digits; pad < width; pad++) {
            writeData('0');
        }
        while (divisor > 0) {
            writeData('0' + magnitude / divisor % 10);
            divisor = divisor / 10;
        }
    }

    /**
     * Prints the first {@code length} bytes of {@code buffer} as raw ASCII characters, starting
     * at the cursor, without wrapping. Unlike {@link #print(String)}, this never builds a runtime
     * {@code String} — for content that already lives in a caller-owned byte buffer (e.g. {@link
     * io.github.jabrena.juno.hardware.net.email.Pop3Client#readSubject}'s output) and would
     * otherwise need a heap allocation just to be displayed.
     */
    public void print(byte[] buffer, int length) {
        for (int i = 0; i < length; i++) {
            writeData(buffer[i]);
        }
    }

    /**
     * Reads the currently pressed button (undebounced), one of {@link #NONE}, {@link #RIGHT},
     * {@link #UP}, {@link #DOWN}, {@link #LEFT}, {@link #SELECT}.
     */
    public int readButton() {
        int reading = gpio.analogRead(pinButtons);
        if (reading < RIGHT_THRESHOLD) {
            return RIGHT;
        }
        if (reading < UP_THRESHOLD) {
            return UP;
        }
        if (reading < DOWN_THRESHOLD) {
            return DOWN;
        }
        if (reading < LEFT_THRESHOLD) {
            return LEFT;
        }
        if (reading < SELECT_THRESHOLD) {
            return SELECT;
        }
        return NONE;
    }

    private void command(int value) {
        gpio.digitalWrite(pinRs, false);
        writeNibble((value >> 4) & 0x0F);
        writeNibble(value & 0x0F);
        Delay.micros(COMMAND_SETTLE_MICROS);
    }

    private void writeData(int value) {
        gpio.digitalWrite(pinRs, true);
        writeNibble((value >> 4) & 0x0F);
        writeNibble(value & 0x0F);
        Delay.micros(COMMAND_SETTLE_MICROS);
    }

    private void writeNibble(int nibble) {
        gpio.digitalWrite(pinD4, (nibble & 0x1) != 0);
        gpio.digitalWrite(pinD5, (nibble & 0x2) != 0);
        gpio.digitalWrite(pinD6, (nibble & 0x4) != 0);
        gpio.digitalWrite(pinD7, (nibble & 0x8) != 0);
        gpio.digitalWrite(pinEnable, true);
        Delay.micros(ENABLE_PULSE_MICROS);
        gpio.digitalWrite(pinEnable, false);
        Delay.micros(NIBBLE_SETTLE_MICROS);
    }
}
