package io.github.jabrena.juno.hardware.device.sd;

import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.lcd.LcdKeypadShield;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.device.wifi.ArduinoWifiShield;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.internal.NativeSdCard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.mock;

@DisplayName("Shield SD cards")
class ShieldSdCardTest {

    static Stream<Arguments> shields() {
        DataLoggerShield dataLogger = new DataLoggerShield();
        LcdKeypadShield lcd = new LcdKeypadShield(mock(Gpio.class));
        ArduinoWifiShield wifi = new ArduinoWifiShield();
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        return Stream.of(
                arguments("Data Logger Shield", (Supplier<SdCard>) dataLogger::getSdCard, 10),
                arguments("TFT touch shield", (Supplier<SdCard>) TftTouchShield::getSdCard, 10),
                arguments("LCD Keypad Shield", (Supplier<SdCard>) lcd::getSdCard, 10),
                arguments("Arduino WiFi Shield", (Supplier<SdCard>) wifi::getSdCard, 4),
                arguments("Nano Connector Carrier", (Supplier<SdCard>) carrier::getSdCard, 4));
    }

    @ParameterizedTest(name = "{0} selects its card on D{2}")
    @MethodSource("shields")
    @DisplayName("Each shield hands out one card bound to its socket's chip select")
    void shouldHandOutOneCardBoundToTheSocketsChipSelect(String shield, Supplier<SdCard> getSdCard, int chipSelect) {
        // When
        SdCard card = getSdCard.get();

        // Then
        assertThat(card).isSameAs(getSdCard.get()).isInstanceOf(NativeSdCard.class);
        assertThat(((NativeSdCard) card).chipSelectPin()).isEqualTo(chipSelect);
    }

    @Test
    @DisplayName("The Data Logger Shield's chip select is D10")
    void shouldSelectTheDataLoggerCardOnD10() {
        // When & Then
        assertThat(new DataLoggerShield().getSdChipSelect()).isEqualTo(10);
    }

    @Test
    @DisplayName("A whole-program emulator can replace the shields' cards")
    void shouldHandOutTheReplacementCard() {
        // Given
        SdCard replacement = mock(SdCard.class);

        // When
        NativeSdCard.setSdCard(replacement);

        // Then
        try {
            assertThat(new DataLoggerShield().getSdCard()).isSameAs(replacement);
        } finally {
            NativeSdCard.setSdCard(null);
        }
    }
}
