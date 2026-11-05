package io.github.jabrena.juno.hardware.device.grove.sensors;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.robotics.LightDetector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Grove Light Sensor v1.2 as a LightDetector")
class GroveLightV12Test {
    private final Gpio pins = mock(Gpio.class);

    @Test
    @DisplayName("Normalizes the ADC sample of its own socket against the 10-bit full scale")
    void shouldNormalizeTheSampleOfItsSocket() {
        // Given
        when(pins.analogRead(Gpio.getA2())).thenReturn(1023, 0);
        LightDetector light = new GroveLightV12(pins, Gpio.getA2(), GroveLightV12.ADC_10_BIT);

        // When
        float bright = light.getNormalizedLightValue();
        float dark = light.getNormalizedLightValue();

        // Then
        assertThat(bright).isEqualTo(light.getHigh());
        assertThat(dark).isEqualTo(light.getLow());
    }

    @Test
    @DisplayName("Reports the light value as a percentage of the full scale")
    void shouldReportThePercentageOfTheFullScale() {
        // Given
        when(pins.analogRead(Gpio.getA2())).thenReturn(512);
        LightDetector light = new GroveLightV12(pins, Gpio.getA2(), GroveLightV12.ADC_10_BIT);

        // When
        float percent = light.getLightValue();

        // Then
        assertThat(percent).isCloseTo(50.05f, within(0.01f));
    }

    @Test
    @DisplayName("Scales a 12-bit ADC sample against the 12-bit full scale")
    void shouldScaleATwelveBitSample() {
        // Given
        when(pins.analogRead(Gpio.getA0())).thenReturn(4095);
        LightDetector light = new GroveLightV12(pins, Gpio.getA0(), GroveLightV12.ADC_12_BIT);

        // When
        float percent = light.getLightValue();

        // Then
        assertThat(percent).isEqualTo(100f);
    }

    @Test
    @DisplayName("Clamps a sample above the declared full scale to the high value")
    void shouldClampASampleAboveTheFullScale() {
        // Given
        when(pins.analogRead(Gpio.getA0())).thenReturn(4095);
        LightDetector light = new GroveLightV12(pins, Gpio.getA0(), GroveLightV12.ADC_10_BIT);

        // When
        float normalized = light.getNormalizedLightValue();

        // Then
        assertThat(normalized).isEqualTo(light.getHigh());
    }

    @Test
    @DisplayName("Rejects a non-positive ADC full scale")
    void shouldRejectANonPositiveFullScale() {
        // When / Then
        assertThatThrownBy(() -> new GroveLightV12(pins, Gpio.getA0(), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Fetches the normalized light value as a one-element sample")
    void shouldFetchTheNormalizedLightValueAsASample() {
        // Given
        when(pins.analogRead(Gpio.getA2())).thenReturn(512);
        GroveLightV12 light = new GroveLightV12(pins, Gpio.getA2(), GroveLightV12.ADC_10_BIT);
        float[] sample = new float[2];

        // When
        light.fetchSample(sample, 1);

        // Then
        assertThat(light.sampleSize()).isEqualTo(1);
        assertThat(sample[1]).isCloseTo(0.5f, within(0.001f));
    }
}
