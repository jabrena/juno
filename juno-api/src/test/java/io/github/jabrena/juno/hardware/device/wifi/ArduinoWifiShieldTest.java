package io.github.jabrena.juno.hardware.device.wifi;

import io.github.jabrena.juno.hardware.internal.NativeWifi;
import io.github.jabrena.juno.hardware.net.Wifi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("Arduino WiFi Shield")
class ArduinoWifiShieldTest {
    @Test
    @DisplayName("The shield hands out the board's Wi-Fi radio")
    void shouldHandOutTheBoardsWifi() {
        // When & Then
        assertThat(new ArduinoWifiShield().getWifi()).isSameAs(NativeWifi.INSTANCE);
    }

    @Test
    @DisplayName("A whole-program emulator can replace the shield's Wi-Fi radio")
    void shouldHandOutTheReplacementWifi() {
        // Given
        Wifi replacement = mock(Wifi.class);

        // When
        NativeWifi.setWifi(replacement);

        // Then
        try {
            assertThat(new ArduinoWifiShield().getWifi()).isSameAs(replacement);
        } finally {
            NativeWifi.setWifi(NativeWifi.INSTANCE);
        }
    }
}
