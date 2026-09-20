package io.github.jabrena.juno.api.io.usb;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BaudRateTest {

    @Test
    void exposesTheSupportedCommonRates() {
        assertThat(BaudRate.values()).containsExactly(
                BaudRate.BAUD_9600,
                BaudRate.BAUD_19200,
                BaudRate.BAUD_38400,
                BaudRate.BAUD_57600,
                BaudRate.BAUD_115200);
        assertThat(BaudRate.values())
                .extracting(BaudRate::bitsPerSecond)
                .containsExactly(9600, 19200, 38400, 57600, 115200);
    }
}
