package io.github.jabrena.juno.thirdparty.grove.sensors;

import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.internal.NativeI2c;
import io.github.jabrena.juno.testing.Wire;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Grove compass probe (BMM150)")
class GroveCompassProbeTest {
    private static final int BMM150_ADDRESS = 0x13;
    private static final int BMM150_CHIP_ID = 0x40;
    private static final int BMM150_DATA_X_LSB = 0x42;
    private static final int BMM150_DATA_Y_LSB = 0x44;
    private static final int BMM150_DATA_Z_LSB = 0x46;
    private static final int BMM150_POWER_CONTROL = 0x4B;
    private static final int BMM150_OPERATION_MODE = 0x4C;

    private I2c bus;
    private GroveCompassProbe probe;

    @BeforeEach
    void startWithAnEmptyBus() {
        Wire.resetBoard();
        bus = mock(I2c.class);
        // A register a test does not set answers -1, like a device that does not acknowledge.
        when(bus.readRegister(anyInt(), anyInt())).thenReturn(-1);
        when(bus.readRegister16(anyInt(), anyInt())).thenReturn(-1);
        probe = new GroveCompassProbe(bus);
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
        when(bus.readRegister(BMM150_ADDRESS, BMM150_CHIP_ID)).thenReturn(0x32);

        // When
        int detected = probe.detect();

        // Then
        assertThat(detected).isEqualTo(GroveCompassProbe.BMM150);
        InOrder order = inOrder(bus);
        order.verify(bus).writeRegister(BMM150_ADDRESS, BMM150_POWER_CONTROL, 0x01);
        order.verify(bus).writeRegister(BMM150_ADDRESS, BMM150_OPERATION_MODE, 0x00);
    }

    @Test
    @DisplayName("Reports no compass when the chip ID is wrong")
    void shouldReportNoCompassWhenTheChipIdIsWrong() {
        // Given
        when(bus.readRegister(BMM150_ADDRESS, BMM150_CHIP_ID)).thenReturn(0x00);

        // When
        int detected = probe.detect();

        // Then
        assertThat(detected).isEqualTo(GroveCompassProbe.NOT_FOUND);
    }

    @Test
    @DisplayName("Reads the three axes from the data registers")
    void shouldReadTheThreeAxesFromTheDataRegisters() {
        // Given
        when(bus.readRegister16(BMM150_ADDRESS, BMM150_DATA_X_LSB)).thenReturn(123 << 3);
        when(bus.readRegister16(BMM150_ADDRESS, BMM150_DATA_Y_LSB)).thenReturn((0x2000 - 123) << 3);
        when(bus.readRegister16(BMM150_ADDRESS, BMM150_DATA_Z_LSB)).thenReturn(456 << 1);
        int[] axes = new int[GroveCompassProbe.AXIS_LENGTH];

        // When
        boolean read = probe.readRaw(axes, axes.length);

        // Then
        assertThat(read).isTrue();
        assertThat(axes).containsExactly(123, -123, 456);
    }

    @Test
    @DisplayName("Reports a failed read when an axis does not answer")
    void shouldReportAFailedReadWhenAnAxisDoesNotAnswer() {
        // Given
        when(bus.readRegister16(BMM150_ADDRESS, BMM150_DATA_X_LSB)).thenReturn(0);
        when(bus.readRegister16(BMM150_ADDRESS, BMM150_DATA_Y_LSB)).thenReturn(0);
        int[] axes = new int[GroveCompassProbe.AXIS_LENGTH];

        // When
        boolean read = probe.readRaw(axes, axes.length);

        // Then
        assertThat(read).isFalse();
    }

    @Test
    @DisplayName("A probe created without a bus talks over the board's I2C bus")
    void shouldTalkOverTheBoardsBusByDefault() {
        // Given
        NativeI2c.setI2c(bus);

        // When
        int detected;
        try {
            detected = new GroveCompassProbe().detect();
        } finally {
            NativeI2c.setI2c(NativeI2c.INSTANCE);
        }

        // Then
        assertThat(detected).isEqualTo(GroveCompassProbe.NOT_FOUND);
        verify(bus).begin();
    }

    @Test
    @DisplayName("Rejects an axis buffer that is too short")
    void shouldRejectAnAxisBufferThatIsTooShort() {
        // When & Then
        assertThatThrownBy(() -> probe.readRaw(new int[2], 2))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
