package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.internal.NativeGpio;
import io.github.jabrena.juno.hardware.internal.NativeSdCard;
import io.github.jabrena.juno.hardware.internal.NativeWifi;
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

    @Test
    @DisplayName("The Cardputer's SD socket is one shared card on GPIO12")
    void shouldHandOutOneSdCardOnGpio12() {
        // When
        SdCard card = M5.getSd();

        // Then
        assertThat(card).isSameAs(M5.getSd()).isInstanceOf(NativeSdCard.class);
        assertThat(((NativeSdCard) card).chipSelectPin()).isEqualTo(12);
    }

    @Test
    @DisplayName("The Cardputer's Wi-Fi radio is one shared instance")
    void shouldHandOutOneWifiInstance() {
        // When & Then
        assertThat(M5.getWifi()).isSameAs(M5.getWifi()).isSameAs(NativeWifi.INSTANCE);
    }
}
