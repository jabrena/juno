package io.github.jabrena.juno.api.thirdparty.grove.sensors;

import io.github.jabrena.juno.api.io.i2c.I2c;
import io.github.jabrena.juno.api.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Grove compass probe (BMM150)")
class GroveCompassProbeTest {
    private static final int BMM150_ADDRESS = 0x13;
    private static final int BMM150_CHIP_ID = 0x40;
    private static final int BMM150_DATA_X_LSB = 0x42;
    private static final int BMM150_DATA_Y_LSB = 0x44;
    private static final int BMM150_DATA_Z_LSB = 0x46;

    @BeforeEach
    void startWithAnEmptyBus() {
        Wire.resetBoard();
        I2c.reset();
    }

    static Stream<Arguments> axisEncodings() {
        return Stream.of(
                Arguments.of(123 << 3, 13, 3, 123),
                Arguments.of((0x2000 - 123) << 3, 13, 3, -123),
                Arguments.of(456 << 1, 15, 1, 456),
                Arguments.of((0x8000 - 456) << 1, 15, 1, -456));
    }

    @ParameterizedTest(name = "decodes raw value {0} with {1} bits and shift {2} as {3}")
    @MethodSource("axisEncodings")
    @DisplayName("Decodes signed BMM150 axis values")
    void shouldDecodeSignedBmm150AxisValues(int raw, int bits, int shift, int expected) {
        // When
        int axis = GroveCompassProbe.decodeAxis(raw, bits, shift);

        // Then
        assertThat(axis).isEqualTo(expected);
    }

    @Test
    @DisplayName("Detects the BMM150 and wakes it up")
    void shouldDetectTheBmm150AndWakeItUp() {
        // Given
        I2c.device(BMM150_ADDRESS, BMM150_CHIP_ID, 0x32);

        // When
        int detected = GroveCompassProbe.detect();

        // Then
        assertThat(detected).isEqualTo(GroveCompassProbe.BMM150);
        assertThat(I2c.writes).extracting(write -> write[2]).containsExactly(0x01, 0x00);
    }

    @Test
    @DisplayName("Reports no compass when the chip ID is wrong")
    void shouldReportNoCompassWhenTheChipIdIsWrong() {
        // Given
        I2c.device(BMM150_ADDRESS, BMM150_CHIP_ID, 0x00);

        // When
        int detected = GroveCompassProbe.detect();

        // Then
        assertThat(detected).isEqualTo(GroveCompassProbe.NOT_FOUND);
    }

    @Test
    @DisplayName("Reads the three axes from the data registers")
    void shouldReadTheThreeAxesFromTheDataRegisters() {
        // Given
        I2c.device(BMM150_ADDRESS, BMM150_DATA_X_LSB, 123 << 3);
        I2c.device(BMM150_ADDRESS, BMM150_DATA_Y_LSB, (0x2000 - 123) << 3);
        I2c.device(BMM150_ADDRESS, BMM150_DATA_Z_LSB, 456 << 1);
        int[] axes = new int[GroveCompassProbe.AXIS_LENGTH];

        // When
        boolean read = GroveCompassProbe.readRaw(axes, axes.length);

        // Then
        assertThat(read).isTrue();
        assertThat(axes).containsExactly(123, -123, 456);
    }

    @Test
    @DisplayName("Reports a failed read when an axis does not answer")
    void shouldReportAFailedReadWhenAnAxisDoesNotAnswer() {
        // Given
        I2c.device(BMM150_ADDRESS, BMM150_DATA_X_LSB, 0);
        I2c.device(BMM150_ADDRESS, BMM150_DATA_Y_LSB, 0);
        int[] axes = new int[GroveCompassProbe.AXIS_LENGTH];

        // When
        boolean read = GroveCompassProbe.readRaw(axes, axes.length);

        // Then
        assertThat(read).isFalse();
    }

    @Test
    @DisplayName("Rejects an axis buffer that is too short")
    void shouldRejectAnAxisBufferThatIsTooShort() {
        // When & Then
        assertThatThrownBy(() -> GroveCompassProbe.readRaw(new int[2], 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
