package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.internal.NativeAudio;
import io.github.jabrena.juno.hardware.internal.NativeBattery;
import io.github.jabrena.juno.hardware.internal.NativeDisplay;
import io.github.jabrena.juno.hardware.internal.NativeKeyboard;
import io.github.jabrena.juno.hardware.internal.NativeMouse;
import io.github.jabrena.juno.hardware.internal.NativeOnboardSdCard;
import io.github.jabrena.juno.hardware.internal.NativeWifi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("MCU")
class MCUTest {

    @AfterEach
    void restoreBoardSdCard() {
        NativeOnboardSdCard.setSdCard(null);
    }

    @Test
    @DisplayName("MCU.getSdCard() hands out one shared onboard card, whatever board hosts the socket")
    void shouldHandOutOneOnboardSdCard() {
        // Given
        SdCard card = mock(SdCard.class);
        NativeOnboardSdCard.setSdCard(card);

        // When & Then
        assertThat(MCU.getSdCard()).isSameAs(card).isSameAs(MCU.getSdCard());
    }

    @Test
    @DisplayName("The board's Wi-Fi radio is one shared instance")
    void shouldHandOutOneWifiInstance() {
        assertThat(MCU.getWifi()).isSameAs(MCU.getWifi()).isSameAs(NativeWifi.INSTANCE);
    }

    @Test
    @DisplayName("The board's display is one shared instance")
    void shouldHandOutOneDisplayInstance() {
        assertThat(MCU.getDisplay()).isSameAs(MCU.getDisplay()).isSameAs(NativeDisplay.INSTANCE);
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
    @DisplayName("The board's keyboard is one shared instance")
    void shouldHandOutOneKeyboardInstance() {
        assertThat(MCU.getKeyboard()).isSameAs(MCU.getKeyboard()).isSameAs(NativeKeyboard.INSTANCE);
    }

    @Test
    @DisplayName("The board's USB HID mouse is one shared instance")
    void shouldHandOutOneMouseInstance() {
        assertThat(MCU.getMouse()).isSameAs(MCU.getMouse()).isSameAs(NativeMouse.INSTANCE);
    }

    @Test
    @DisplayName("The board's speaker is one shared instance")
    void shouldHandOutOneAudioInstance() {
        assertThat(MCU.getAudio()).isSameAs(MCU.getAudio()).isSameAs(NativeAudio.INSTANCE);
    }

    @Test
    @DisplayName("The board's battery is one shared instance")
    void shouldHandOutOneBatteryInstance() {
        assertThat(MCU.getBattery()).isSameAs(MCU.getBattery()).isSameAs(NativeBattery.INSTANCE);
    }
}
