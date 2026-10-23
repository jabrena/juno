package io.github.jabrena.juno.hardware.net;

import io.github.jabrena.juno.hardware.ArduinoOne;
import io.github.jabrena.juno.hardware.internal.NativeWifi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Wifi")
class WifiTest {
    @Test
    @DisplayName("The portable development access point is open and named JunoAP")
    void shouldExposeOpenDevelopmentAccessPointDefaults() {
        // Then
        assertThat(Wifi.DEFAULT_ACCESS_POINT_SSID).isEqualTo("JunoAP");
        assertThat(Wifi.DEFAULT_ACCESS_POINT_PASSWORD).isEmpty();
    }

    @Test
    @DisplayName("The board's Wi-Fi radio is one shared instance")
    void shouldHandOutOneInstanceForTheBoard() {
        // When & Then
        assertThat(ArduinoOne.getWifi()).isSameAs(ArduinoOne.getWifi()).isSameAs(NativeWifi.INSTANCE);
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
            assertThat(ArduinoOne.getWifi()).isSameAs(replacement);
        } finally {
            NativeWifi.setWifi(NativeWifi.INSTANCE);
        }
    }
}
