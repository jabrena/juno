package io.github.jabrena.juno.api.thirdparty.lcd;

import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LcdKeypadShieldTest {
    private static final int PIN_RS = 8;
    private static final int PIN_ENABLE = 9;
    private static final int PIN_BACKLIGHT = 10;
    private static final int PIN_BUTTONS = 14;

    @BeforeEach
    void startWithAnIdleShield() {
        Wire.resetBoard();
        Gpio.analogValues[PIN_BUTTONS] = 1023;
    }

    @Test
    void beginRunsTheFourBitStartupThenConfiguresTheDisplay() {
        LcdKeypadShield.begin();

        assertThat(emitted()).containsSubsequence("3@0", "3@0", "3@0", "2@0",
                "2@0", "8@0", "0@0", "C@0", "0@0", "1@0", "0@0", "6@0");
    }

    @Test
    void setCursorAddsTheRowOffsetToTheDdramAddress() {
        LcdKeypadShield.setCursor(3, 1);

        assertThat(emitted()).containsExactly("C@0", "3@0");
    }

    @Test
    void homeAndClearSendTheirCommands() {
        LcdKeypadShield.home();
        assertThat(emitted()).containsExactly("0@0", "2@0");

        Wire.resetBoard();
        LcdKeypadShield.clear();
        assertThat(emitted()).containsExactly("0@0", "1@0");
    }

    @Test
    void printSendsEachCharacterAsDataWithRsHigh() {
        LcdKeypadShield.print("A");

        assertThat(emitted()).containsExactly("4@1", "1@1");
    }

    @Test
    void printOfAnIntegerAndOfABufferSendsTheirDigits() {
        LcdKeypadShield.print(7);
        assertThat(emitted()).containsExactly("3@1", "7@1");

        Wire.resetBoard();
        LcdKeypadShield.print(new byte[] {'H', 'i'}, 2);
        assertThat(emitted()).containsExactly("4@1", "8@1", "6@1", "9@1");
    }

    @Test
    void backlightDrivesThePinOnlyWhenItIsSwitchedOn() {
        LcdKeypadShield.backlight(true);
        assertThat(Gpio.lastPinMode[PIN_BACKLIGHT]).isEqualTo(Gpio.INPUT);

        LcdKeypadShield.backlight(false);
        assertThat(Gpio.lastPinMode[PIN_BACKLIGHT]).isEqualTo(Gpio.OUTPUT);
    }

    @Test
    void readButtonMapsTheAnalogLadderToItsKey() {
        assertButton(0, LcdKeypadShield.RIGHT);
        assertButton(100, LcdKeypadShield.UP);
        assertButton(300, LcdKeypadShield.DOWN);
        assertButton(500, LcdKeypadShield.LEFT);
        assertButton(700, LcdKeypadShield.SELECT);
        assertButton(1000, LcdKeypadShield.NONE);
    }

    private static void assertButton(int reading, int key) {
        Gpio.analogValues[PIN_BUTTONS] = reading;

        assertThat(LcdKeypadShield.readButton()).as("reading %d", reading).isEqualTo(key);
    }

    /**
     * What the controller latched: each nibble on the data lines when the enable line fell, as {@code <nibble>@<rs>}
     * with the nibble in hex.
     */
    private static List<String> emitted() {
        List<String> latched = new ArrayList<>();
        int[] data = new int[4];
        int rs = 0;
        for (int[] write : Gpio.writes) {
            int pin = write[0];
            int value = write[1];
            if (pin >= 4 && pin <= 7) {
                data[pin - 4] = value;
            } else if (pin == PIN_RS) {
                rs = value;
            } else if (pin == PIN_ENABLE && value == 0) {
                int nibble = data[0] | data[1] << 1 | data[2] << 2 | data[3] << 3;
                latched.add(Integer.toHexString(nibble).toUpperCase() + "@" + rs);
            }
        }
        return latched;
    }
}
