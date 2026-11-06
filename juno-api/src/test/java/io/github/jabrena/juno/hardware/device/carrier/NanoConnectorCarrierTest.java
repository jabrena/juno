package io.github.jabrena.juno.hardware.device.carrier;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Nano Connector Carrier pin mapping")
class NanoConnectorCarrierTest {

    @Test
    @DisplayName("Maps the microSD slot to SPI with chip select on D4, or D2/D3 through its jumpers")
    void shouldMapTheMicroSdSlot() {
        Gpio pins = pins();
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(pins);
        // When & Then
        assertThat(new int[] {
                carrier.getSdChipSelect(),
                carrier.getSdChipSelectJumperD2(),
                carrier.getSdChipSelectJumperD3(),
                carrier.getSpiMosi(),
                carrier.getSpiMiso(),
                carrier.getSpiSck()})
            .containsExactly(pins.digitalPin("D4"), pins.digitalPin("D2"), pins.digitalPin("D3"),
                    pins.digitalPin("D11"), pins.digitalPin("D12"), pins.digitalPin("D13"));
    }

    @Test
    @DisplayName("Maps each single-signal Grove socket to its yellow wire")
    void shouldMapEachGroveSocketToItsSignalWire() {
        Gpio pins = pins();
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(pins);
        // When & Then
        assertThat(new int[] {
                carrier.getGroveA0(),
                carrier.getGroveA2(),
                carrier.getGroveUart()})
            .containsExactly(pins.analogPin("A0"), pins.analogPin("A2"), pins.digitalPin("D0"));
    }

    @Test
    @DisplayName("Maps the analog Grove sockets to A0/A1 and A2/A3")
    void shouldMapTheAnalogGroveSockets() {
        Gpio pins = pins();
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(pins);
        // When & Then
        assertThat(new int[] {
                carrier.getA0(),
                carrier.getA1(),
                carrier.getA2(),
                carrier.getA3()})
            .containsExactly(pins.analogPin("A0"), pins.analogPin("A1"), pins.analogPin("A2"),
                    pins.analogPin("A3"));
    }

    @Test
    @DisplayName("Maps the I2C socket and Qwiic to A4/A5 and the UART socket to D0/D1")
    void shouldMapTheI2cAndUartSockets() {
        Gpio pins = pins();
        NanoConnectorCarrier carrier = new NanoConnectorCarrier(pins);
        // When & Then
        assertThat(new int[] {
                carrier.getI2cSda(),
                carrier.getI2cScl(),
                carrier.getUartRx(),
                carrier.getUartTx()})
            .containsExactly(pins.analogPin("A4"), pins.analogPin("A5"), pins.digitalPin("D0"),
                    pins.digitalPin("D1"));
    }

    private static Gpio pins() {
        Gpio pins = mock(Gpio.class);
        when(pins.digitalPin(anyString())).thenAnswer(invocation -> Integer.parseInt(
                invocation.<String>getArgument(0).substring(1)));
        when(pins.analogPin(anyString())).thenAnswer(invocation -> 14 + Integer.parseInt(
                invocation.<String>getArgument(0).substring(1)));
        return pins;
    }
}
