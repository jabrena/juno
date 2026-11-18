package io.github.jabrena.juno.hardware.device.carrier;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Portenta Hat Carrier pin map")
class PortentaHatCarrierTest {

    @Test
    @DisplayName("names the display and touch lines by the Juno pins the H7 resolves them to")
    void namesTheHeaderPins() {
        Gpio pins = mock(Gpio.class);
        when(pins.digitalPin("D7")).thenReturn(7);
        when(pins.digitalPin("GPIO161")).thenReturn(161);
        when(pins.digitalPin("D5")).thenReturn(5);
        when(pins.digitalPin("D4")).thenReturn(4);

        PortentaHatCarrier carrier = new PortentaHatCarrier(pins);

        assertThat(carrier.getLcdChipSelect()).isEqualTo(7);
        assertThat(carrier.getLcdDataCommand()).isEqualTo(161);
        assertThat(carrier.getLcdReset()).isEqualTo(5);
        assertThat(carrier.getTouchChipSelect()).isEqualTo(4);
    }
}
