package io.github.jabrena.juno.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("Delay")
class DelayTest {

    @Test
    @DisplayName("Non-positive periods return immediately")
    void shouldIgnoreNonPositivePeriods() {
        // When & Then
        assertThatCode(() -> {
            Delay.millis(0);
            Delay.millis(-1);
            Delay.micros(0);
            Delay.micros(-1);
            Delay.seconds(0);
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("An interrupted sleep preserves the interrupted state")
    void shouldPreserveTheInterruptedState() {
        // Given
        Thread.currentThread().interrupt();

        try {
            // When
            Delay.millis(1);

            // Then
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }
}
