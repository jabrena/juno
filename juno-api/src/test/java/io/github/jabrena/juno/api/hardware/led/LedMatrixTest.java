package io.github.jabrena.juno.api.hardware.led;

import io.github.jabrena.juno.api.hardware.MCU;
import io.github.jabrena.juno.api.hardware.internal.NativeLedMatrix;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@DisplayName("LED matrix")
class LedMatrixTest {
    @Test
    @DisplayName("The board's LED matrix is one shared instance")
    void shouldHandOutOneInstanceForTheBoard() {
        // When & Then
        assertThat(MCU.getLedMatrix()).isSameAs(MCU.getLedMatrix()).isSameAs(NativeLedMatrix.INSTANCE);
    }

    @Test
    @DisplayName("A whole-program emulator can replace the board's LED matrix")
    void shouldHandOutTheReplacementLedMatrix() {
        // Given
        LedMatrix replacement = mock(LedMatrix.class);

        // When
        NativeLedMatrix.setLedMatrix(replacement);

        // Then
        try {
            assertThat(MCU.getLedMatrix()).isSameAs(replacement);
        } finally {
            NativeLedMatrix.setLedMatrix(NativeLedMatrix.INSTANCE);
        }
    }

    @Test
    @DisplayName("A three-word frame loads with its fourth word zero")
    void shouldLoadAThreeWordFrameWithItsFourthWordZero() {
        // Given
        LedMatrix matrix = mock(LedMatrix.class, CALLS_REAL_METHODS);

        // When
        matrix.loadFrame(1, 2, 3);

        // Then
        verify(matrix).loadFrame(1, 2, 3, 0);
    }
}
