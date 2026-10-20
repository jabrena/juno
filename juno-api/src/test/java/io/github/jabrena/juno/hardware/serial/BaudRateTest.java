package io.github.jabrena.juno.hardware.serial;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Serial baud rates")
class BaudRateTest {

    @Test
    @DisplayName("Exposes the supported common rates")
    void shouldExposeTheSupportedCommonRates() {
        // When & Then
        assertThat(BaudRate.values()).containsExactly(
                BaudRate.BAUD_9600,
                BaudRate.BAUD_19200,
                BaudRate.BAUD_38400,
                BaudRate.BAUD_57600,
                BaudRate.BAUD_115200);
    }

    @Test
    @DisplayName("Reports each rate in bits per second")
    void shouldReportEachRateInBitsPerSecond() {
        // When & Then
        assertThat(BaudRate.values())
                .extracting(BaudRate::bitsPerSecond)
                .containsExactly(9600, 19200, 38400, 57600, 115200);
    }
}
