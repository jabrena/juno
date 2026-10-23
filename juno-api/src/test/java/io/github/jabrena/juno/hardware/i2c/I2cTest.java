package io.github.jabrena.juno.hardware.i2c;

import io.github.jabrena.juno.hardware.ArduinoOne;
import io.github.jabrena.juno.hardware.internal.NativeI2c;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("I2c")
class I2cTest {
    @Test
    @DisplayName("The board's I2C bus is one shared instance")
    void shouldHandOutOneInstanceForTheBoard() {
        // When & Then
        assertThat(ArduinoOne.getI2c()).isSameAs(ArduinoOne.getI2c()).isSameAs(NativeI2c.INSTANCE);
    }

    @Test
    @DisplayName("A whole-program emulator can replace the board's I2C bus")
    void shouldHandOutTheReplacementBus() {
        // Given
        I2c replacement = mock(I2c.class);

        // When
        NativeI2c.setI2c(replacement);

        // Then
        try {
            assertThat(ArduinoOne.getI2c()).isSameAs(replacement);
        } finally {
            NativeI2c.setI2c(NativeI2c.INSTANCE);
        }
    }
}
