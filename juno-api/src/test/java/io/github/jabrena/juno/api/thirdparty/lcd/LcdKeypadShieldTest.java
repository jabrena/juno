package io.github.jabrena.juno.api.thirdparty.lcd;

import io.github.jabrena.juno.api.io.gpio.Gpio;
import io.github.jabrena.juno.api.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LCD keypad shield")
class LcdKeypadShieldTest {
    private static final int PIN_RS = 8;
    private static final int PIN_ENABLE = 9;
    private static final int PIN_BACKLIGHT = 10;
    private static final int PIN_BUTTONS = 14;

    private Gpio pins;
    private LcdKeypadShield lcd;

    @BeforeEach
    void startWithAnIdleShield() {
        Wire.resetBoard();
        pins = mock(Gpio.class);
        lcd = new LcdKeypadShield(pins);
        when(pins.analogRead(PIN_BUTTONS)).thenReturn(1023);
    }

    static Stream<Arguments> buttonLadder() {
        return Stream.of(
                Arguments.of(0, LcdKeypadShield.RIGHT),
                Arguments.of(100, LcdKeypadShield.UP),
                Arguments.of(300, LcdKeypadShield.DOWN),
                Arguments.of(500, LcdKeypadShield.LEFT),
                Arguments.of(700, LcdKeypadShield.SELECT),
                Arguments.of(1000, LcdKeypadShield.NONE));
    }

    @Test
    @DisplayName("Begin runs the four-bit startup and then configures the display")
    void shouldRunTheFourBitStartupThenConfigureTheDisplay() {
        // When
        lcd.begin();

        // Then
        assertThat(emitted()).containsSubsequence("3@0", "3@0", "3@0", "2@0",
                "2@0", "8@0", "0@0", "C@0", "0@0", "1@0", "0@0", "6@0");
    }

    @Test
    @DisplayName("Set cursor adds the row offset to the DDRAM address")
    void shouldAddTheRowOffsetToTheDdramAddressWhenSettingTheCursor() {
        // When
        lcd.setCursor(3, 1);

        // Then
        assertThat(emitted()).containsExactly("C@0", "3@0");
    }

    @Test
    @DisplayName("Home sends the home command")
    void shouldSendTheHomeCommand() {
        // When
        lcd.home();

        // Then
        assertThat(emitted()).containsExactly("0@0", "2@0");
    }

    @Test
    @DisplayName("Clear sends the clear command")
    void shouldSendTheClearCommand() {
        // When
        lcd.clear();

        // Then
        assertThat(emitted()).containsExactly("0@0", "1@0");
    }

    @Test
    @DisplayName("Print sends each character as data with RS high")
    void shouldSendEachCharacterAsDataWithRsHigh() {
        // When
        lcd.print("A");

        // Then
        assertThat(emitted()).containsExactly("4@1", "1@1");
    }

    @Test
    @DisplayName("Print of an integer sends its digits")
    void shouldSendTheDigitsOfAnInteger() {
        // When
        lcd.print(7);

        // Then
        assertThat(emitted()).containsExactly("3@1", "7@1");
    }

    @Test
    @DisplayName("Print of a buffer sends its characters")
    void shouldSendTheCharactersOfABuffer() {
        // When
        lcd.print(new byte[] {'H', 'i'}, 2);

        // Then
        assertThat(emitted()).containsExactly("4@1", "8@1", "6@1", "9@1");
    }

    @Test
    @DisplayName("Backlight drives the pin as an input when switched on")
    void shouldDriveTheBacklightPinAsInputWhenSwitchedOn() {
        // When
        lcd.backlight(true);

        // Then
        verify(pins).pinMode(PIN_BACKLIGHT, Gpio.getInput());
    }

    @Test
    @DisplayName("Backlight drives the pin as an output when switched off")
    void shouldDriveTheBacklightPinAsOutputWhenSwitchedOff() {
        // When
        lcd.backlight(false);

        // Then
        verify(pins).pinMode(PIN_BACKLIGHT, Gpio.getOutput());
    }

    @ParameterizedTest(name = "reading {0} maps to key {1}")
    @MethodSource("buttonLadder")
    @DisplayName("Read button maps the analog ladder to its key")
    void shouldMapTheAnalogLadderToItsKey(int reading, int key) {
        // Given
        when(pins.analogRead(PIN_BUTTONS)).thenReturn(reading);

        // When
        int button = lcd.readButton();

        // Then
        assertThat(button).as("reading %d", reading).isEqualTo(key);
    }

    /**
     * What the controller latched: each nibble on the data lines when the enable line fell, as {@code <nibble>@<rs>}
     * with the nibble in hex.
     */
    private List<String> emitted() {
        ArgumentCaptor<Integer> pinsWritten = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Boolean> levels = ArgumentCaptor.forClass(Boolean.class);
        verify(pins, atLeast(0)).digitalWrite(pinsWritten.capture(), levels.capture());
        List<String> latched = new ArrayList<>();
        int[] data = new int[4];
        int rs = 0;
        for (int write = 0; write < pinsWritten.getAllValues().size(); write++) {
            int pin = pinsWritten.getAllValues().get(write);
            int value = levels.getAllValues().get(write) ? 1 : 0;
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
