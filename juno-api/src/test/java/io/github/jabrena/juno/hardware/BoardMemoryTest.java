package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.internal.NativeBoardMemory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("BoardMemory")
class BoardMemoryTest {
    @Test
    @DisplayName("The board's memory is one shared instance")
    void shouldHandOutOneInstanceForTheBoard() {
        // When & Then
        assertThat(Arduino.getBoardMemory()).isSameAs(Arduino.getBoardMemory()).isSameAs(NativeBoardMemory.INSTANCE);
    }

    @Test
    @DisplayName("A whole-program emulator can replace the board's memory")
    void shouldHandOutTheReplacementBoardMemory() {
        // Given
        BoardMemory emptyArena = mock(BoardMemory.class);
        when(emptyArena.arenaCapacityBytes()).thenReturn(8192);

        // When
        NativeBoardMemory.setBoardMemory(emptyArena);

        // Then
        try {
            assertThat(Arduino.getBoardMemory()).isSameAs(emptyArena);
            assertThat(Arduino.getBoardMemory().arenaCapacityBytes()).isEqualTo(8192);
        } finally {
            NativeBoardMemory.setBoardMemory(NativeBoardMemory.INSTANCE);
        }
    }
}
