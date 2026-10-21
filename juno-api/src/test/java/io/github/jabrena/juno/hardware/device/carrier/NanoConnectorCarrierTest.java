package io.github.jabrena.juno.hardware.device.carrier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Nano Connector Carrier pin mapping")
class NanoConnectorCarrierTest {

    @Test
    @DisplayName("Maps the microSD slot to SPI with chip select on D4, or D2/D3 through its jumpers")
    void shouldMapTheMicroSdSlot() {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        // When & Then
        assertThat(new int[] {
                carrier.getSdChipSelect(),
                carrier.getSdChipSelectJumperD2(),
                carrier.getSdChipSelectJumperD3(),
                carrier.getSpiMosi(),
                carrier.getSpiMiso(),
                carrier.getSpiSck()})
            .containsExactly(4, 2, 3, 11, 12, 13);
    }

    @Test
    @DisplayName("Maps each single-signal Grove socket to its yellow wire")
    void shouldMapEachGroveSocketToItsSignalWire() {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        // When & Then
        assertThat(new int[] {
                carrier.getGroveA0(),
                carrier.getGroveA2(),
                carrier.getGroveUart()})
            .containsExactly(14, 16, 0);
    }

    @Test
    @DisplayName("Maps the analog Grove sockets to A0/A1 and A2/A3")
    void shouldMapTheAnalogGroveSockets() {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        // When & Then
        assertThat(new int[] {
                carrier.getA0(),
                carrier.getA1(),
                carrier.getA2(),
                carrier.getA3()})
            .containsExactly(14, 15, 16, 17);
    }

    @Test
    @DisplayName("Maps the I2C socket and Qwiic to A4/A5 and the UART socket to D0/D1")
    void shouldMapTheI2cAndUartSockets() {
        NanoConnectorCarrier carrier = new NanoConnectorCarrier();
        // When & Then
        assertThat(new int[] {
                carrier.getI2cSda(),
                carrier.getI2cScl(),
                carrier.getUartRx(),
                carrier.getUartTx()})
            .containsExactly(18, 19, 0, 1);
    }
}
