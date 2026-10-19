package io.github.jabrena.juno.api.time;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("Clock")
class ClockTest {
    private Clock clock;

    @BeforeEach
    void mockTheClock() {
        clock = mock(Clock.class);
    }

    @Test
    @DisplayName("Readings come from its port")
    void shouldReadFromItsPort() {
        // Given
        when(clock.millis()).thenReturn(1_500);
        when(clock.micros()).thenReturn(1_500_250);

        // When & Then
        assertThat(clock.millis()).isEqualTo(1_500);
        assertThat(clock.micros()).isEqualTo(1_500_250);
    }
}
