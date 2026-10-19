package io.github.jabrena.juno.api.io.serial;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Serial port output")
class SerialTest {

    @BeforeEach
    void openAnEmptyPort() {
        Serial.reset();
    }

    @Test
    @DisplayName("Begin opens the port at a common baud rate")
    void shouldOpenThePortAtACommonBaudRate() {
        // When
        Serial.begin(BaudRate.BAUD_115200);

        // Then
        assertThat(Serial.baudRate).isEqualTo(115_200);
    }

    @Test
    @DisplayName("Begin opens the port at a raw baud rate")
    void shouldOpenThePortAtARawBaudRate() {
        // When
        Serial.begin(9_600);

        // Then
        assertThat(Serial.baudRate).isEqualTo(9_600);
    }

    @Test
    @DisplayName("Booleans print as words")
    void shouldPrintBooleansAsWords() {
        // When
        Serial.print(true);
        Serial.println(false);

        // Then
        assertThat(Serial.output).containsExactly("true", "false\n");
    }

    @Test
    @DisplayName("Numbers print with or without a line ending")
    void shouldPrintNumbersWithOrWithoutALineEnding() {
        // When
        Serial.print(42);
        Serial.println(7L);
        Serial.print(1.5f);
        Serial.println(2.5d);

        // Then
        assertThat(Serial.output).containsExactly("42", "7\n", "1.5", "2.5\n");
    }

    @Test
    @DisplayName("Strings print with or without a line ending")
    void shouldPrintStringsWithOrWithoutALineEnding() {
        // When
        Serial.print("hi");
        Serial.println("there");

        // Then
        assertThat(Serial.output).containsExactly("hi", "there\n");
    }
}
