package io.github.jabrena.juno.hardware.device;

import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveLightV12;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.internal.NativeGpio;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Each convenience constructor takes its collaborator from {@code MCU}; the explicit constructor takes the same object.
 * With {@code MCU} replaced by a mock, both forms must drive the mock identically.
 */
@DisplayName("Convenience constructors match their injectable forms")
class McuConvenienceConstructorsTest {

    @AfterEach
    void restoreBoardGpio() {
        NativeGpio.setGpio(NativeGpio.INSTANCE);
    }

    @Test
    @DisplayName("GroveBaseShield() reads the board's pins exactly as GroveBaseShield(MCU.getGpio()) does")
    void shouldResolveTheShieldFromTheBoardGpio() {
        // Given
        Gpio gpio = mock(Gpio.class);
        when(gpio.analogPin("A0")).thenReturn(14);
        when(gpio.analogPin("A1")).thenReturn(15);
        NativeGpio.setGpio(gpio);

        // When
        GroveBaseShield convenience = new GroveBaseShield();
        GroveBaseShield explicit = new GroveBaseShield(gpio);

        // Then
        assertThat(convenience.getA0()).isEqualTo(explicit.getA0()).isEqualTo(14);
        assertThat(convenience.getA1()).isEqualTo(explicit.getA1()).isEqualTo(15);
        verify(gpio, org.mockito.Mockito.times(2)).analogPin("A0");
    }

    @Test
    @DisplayName("NanoConnectorCarrier() resolves its chip select from the board's pins")
    void shouldResolveTheCarrierFromTheBoardGpio() {
        // Given
        Gpio gpio = mock(Gpio.class);
        when(gpio.digitalPin("D4")).thenReturn(4);
        NativeGpio.setGpio(gpio);

        // When
        NanoConnectorCarrier convenience = new NanoConnectorCarrier();
        NanoConnectorCarrier explicit = new NanoConnectorCarrier(gpio);

        // Then
        assertThat(convenience.getSdChipSelect()).isEqualTo(explicit.getSdChipSelect()).isEqualTo(4);
    }

    @Test
    @DisplayName("GroveLightV12(pin, adc) samples the board's analog pin as GroveLightV12(MCU.getGpio(), pin, adc) does")
    void shouldSampleTheLightSensorFromTheBoardGpio() {
        // Given
        Gpio gpio = mock(Gpio.class);
        when(gpio.analogRead(3)).thenReturn(512);
        NativeGpio.setGpio(gpio);

        // When
        float convenience = new GroveLightV12(3, GroveLightV12.ADC_10_BIT).getLightValue();
        float explicit = new GroveLightV12(gpio, 3, GroveLightV12.ADC_10_BIT).getLightValue();

        // Then
        assertThat(convenience).isEqualTo(explicit);
    }
}
