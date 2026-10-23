package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.internal.NativeAudio;
import io.github.jabrena.juno.hardware.internal.NativeDisplay;
import io.github.jabrena.juno.hardware.internal.NativeKeyboard;
import io.github.jabrena.juno.hardware.internal.NativeSdCard;
import io.github.jabrena.juno.hardware.internal.NativeWifi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("M5StackCardputer")
class M5Test {
    @Test
    @DisplayName("The Cardputer's SD socket is one shared card on GPIO12")
    void shouldHandOutOneSdCardOnGpio12() {
        // When
        SdCard card = M5StackCardputer.getSd();

        // Then
        assertThat(card).isSameAs(M5StackCardputer.getSd()).isInstanceOf(NativeSdCard.class);
        assertThat(((NativeSdCard) card).chipSelectPin()).isEqualTo(12);
    }

    @Test
    @DisplayName("The Cardputer's Wi-Fi radio is one shared instance")
    void shouldHandOutOneWifiInstance() {
        // When & Then
        assertThat(M5StackCardputer.getWifi()).isSameAs(M5StackCardputer.getWifi()).isSameAs(NativeWifi.INSTANCE);
    }

    @Test
    @DisplayName("The Cardputer's display is one shared instance")
    void shouldHandOutOneDisplayInstance() {
        // When & Then
        assertThat(M5StackCardputer.getDisplay()).isSameAs(M5StackCardputer.getDisplay()).isSameAs(NativeDisplay.INSTANCE);
    }

    @Test
    @DisplayName("RGB channels are packed as RGB565")
    void shouldPackDisplayColorsAsRgb565() {
        assertThat(Display.color(255, 255, 255)).isEqualTo(Display.WHITE);
        assertThat(Display.color(255, 0, 0)).isEqualTo(Display.RED);
        assertThat(Display.color(0, 255, 0)).isEqualTo(Display.GREEN);
        assertThat(Display.color(0, 0, 255)).isEqualTo(Display.BLUE);
    }

    @Test
    @DisplayName("The Cardputer's keyboard is one shared instance")
    void shouldHandOutOneKeyboardInstance() {
        // When & Then
        assertThat(M5StackCardputer.getKeyboard()).isSameAs(M5StackCardputer.getKeyboard()).isSameAs(NativeKeyboard.INSTANCE);
    }

    @Test
    @DisplayName("The Cardputer's speaker is one shared instance")
    void shouldHandOutOneAudioInstance() {
        assertThat(M5StackCardputer.getAudio()).isSameAs(M5StackCardputer.getAudio()).isSameAs(NativeAudio.INSTANCE);
    }
}
