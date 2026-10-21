package io.github.jabrena.juno.hardware.net;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.internal.NativeWifi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Wifi")
class WifiTest {
    @Test
    @DisplayName("The board's Wi-Fi radio is one shared instance")
    void shouldHandOutOneInstanceForTheBoard() {
        // When & Then
        assertThat(MCU.getWifi()).isSameAs(MCU.getWifi()).isSameAs(NativeWifi.INSTANCE);
    }

    @Test
    @DisplayName("A whole-program emulator can replace the board's Wi-Fi radio")
    void shouldHandOutTheReplacementWifi() {
        // Given
        Wifi replacement = mock(Wifi.class);

        // When
        NativeWifi.setWifi(replacement);

        // Then
        try {
            assertThat(MCU.getWifi()).isSameAs(replacement);
        } finally {
            NativeWifi.setWifi(NativeWifi.INSTANCE);
        }
    }
}
