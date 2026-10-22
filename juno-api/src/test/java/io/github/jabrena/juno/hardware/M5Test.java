package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.internal.NativeGpio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("M5")
class M5Test {
    @Test
    @DisplayName("The Cardputer's pins are one shared instance")
    void shouldHandOutOneGpioInstance() {
        // When & Then
        assertThat(M5.getGpio()).isSameAs(M5.getGpio()).isSameAs(NativeGpio.INSTANCE);
    }
}
