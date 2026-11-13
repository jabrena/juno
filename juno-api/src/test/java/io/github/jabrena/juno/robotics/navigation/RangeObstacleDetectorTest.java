package io.github.jabrena.juno.robotics.navigation;

import io.github.jabrena.juno.robotics.RangeFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RangeObstacleDetector: confirmed, with hysteresis")
class RangeObstacleDetectorTest {

    /** Returns the next of its readings, in metres, on each call. */
    private static final class ScriptedRanges implements RangeFinder {
        private final float[] metres;
        private int next;

        ScriptedRanges(float... metres) {
            this.metres = metres;
        }

        @Override
        public float getRange() {
            return metres[next++];
        }
    }

    @Test
    @DisplayName("Two near readings in a row block the way, and the range is reported in millimetres")
    void shouldBlockAfterTheConfirmingReadings() {
        // Given: stop under 250 mm, clear beyond 350 mm, two readings to confirm
        RangeObstacleDetector detector = new RangeObstacleDetector(new ScriptedRanges(0.2f, 0.19f), 250, 350, 2);

        // When & Then
        assertThat(detector.blocked()).isFalse();
        assertThat(detector.blocked()).isTrue();
        assertThat(detector.rangeMillimetres()).isEqualTo(190);
    }

    @Test
    @DisplayName("A single stray echo does not block the way")
    void shouldIgnoreASingleStrayEcho() {
        // Given
        RangeObstacleDetector detector = new RangeObstacleDetector(new ScriptedRanges(0.1f, 1.0f, 0.1f), 250, 350, 2);

        // When & Then
        assertThat(detector.blocked()).isFalse();
        assertThat(detector.blocked()).isFalse();
        assertThat(detector.blocked()).isFalse();
    }

    @Test
    @DisplayName("A reading between the two distances keeps the way blocked; one beyond, or no echo, clears it")
    void shouldClearOnlyBeyondTheClearDistance() {
        // Given
        RangeObstacleDetector detector = new RangeObstacleDetector(
                new ScriptedRanges(0.2f, 0.3f, 0.4f, 0.2f, Float.NaN), 250, 350, 1);

        // When & Then
        assertThat(detector.blocked()).isTrue();
        assertThat(detector.blocked()).isTrue();
        assertThat(detector.blocked()).isFalse();
        assertThat(detector.blocked()).isTrue();
        assertThat(detector.blocked()).isFalse();
    }

    @Test
    @DisplayName("A reset forgets a blocked way")
    void shouldForgetOnReset() {
        // Given
        RangeObstacleDetector detector = new RangeObstacleDetector(new ScriptedRanges(0.1f, 0.3f), 250, 350, 1);
        detector.blocked();

        // When
        detector.reset();

        // Then: 300 mm alone would keep a blocked way blocked
        assertThat(detector.blocked()).isFalse();
    }

    @Test
    @DisplayName("Distances out of order or no confirming reading are refused")
    void shouldRejectInvalidSettings() {
        ScriptedRanges ranges = new ScriptedRanges();

        assertThatThrownBy(() -> new RangeObstacleDetector(ranges, 0, 350, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RangeObstacleDetector(ranges, 400, 350, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RangeObstacleDetector(ranges, 250, 350, 0)).isInstanceOf(IllegalArgumentException.class);
    }
}
