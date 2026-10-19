package io.github.jabrena.juno.api.thirdparty.grove;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Grove base shield socket mapping")
class GroveBaseShieldTest {

    @Test
    @DisplayName("Maps the Grove sockets to UNO pins")
    void shouldMapTheGroveSocketsToUnoPins() {
        // When & Then
        assertThat(new int[] {
                GroveBaseShield.A0,
                GroveBaseShield.D2,
                GroveBaseShield.D8,
                GroveBaseShield.UART_RX,
                GroveBaseShield.I2C_SCL})
            .containsExactly(14, 2, 8, 0, 19);
    }
}
