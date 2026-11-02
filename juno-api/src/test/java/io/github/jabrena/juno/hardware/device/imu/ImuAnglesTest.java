package io.github.jabrena.juno.hardware.device.imu;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("Integer tilt maths")
class ImuAnglesTest {

    @Test
    @DisplayName("atan2 gives the quadrant-correct angle in milli-degrees")
    void shouldMatchAtan2InEveryQuadrant() {
        assertThat(ImuAngles.atan2Millidegrees(1, 1)).isEqualTo(45_000);
        assertThat(ImuAngles.atan2Millidegrees(0, 1)).isEqualTo(0);
        assertThat(ImuAngles.atan2Millidegrees(-1, 0)).isEqualTo(-90_000);
        assertThat(ImuAngles.atan2Millidegrees(1, -1)).isEqualTo(135_000);
        assertThat(ImuAngles.atan2Millidegrees(0, -1)).isEqualTo(180_000);
        assertThat(ImuAngles.atan2Millidegrees(-1, -1)).isEqualTo(-135_000);
    }

    @Test
    @DisplayName("atan2 stays within a tenth of a degree between table steps")
    void shouldInterpolateBetweenTableSteps() {
        assertThat(ImuAngles.atan2Millidegrees(1, 3)).isCloseTo(18_435, within(100));
        assertThat(ImuAngles.atan2Millidegrees(-5, 16)).isCloseTo(-17_354, within(100));
    }

    @Test
    @DisplayName("atan2 of the zero vector is zero")
    void shouldReturnZeroForTheZeroVector() {
        assertThat(ImuAngles.atan2Millidegrees(0, 0)).isZero();
    }

    @Test
    @DisplayName("magnitude rounds the square root down")
    void shouldComputeTheIntegerSquareRoot() {
        assertThat(ImuAngles.magnitude(3, 4)).isEqualTo(5);
        assertThat(ImuAngles.magnitude(0, 0)).isZero();
        assertThat(ImuAngles.magnitude(32_767, 32_767)).isEqualTo(46_339);
    }
}
