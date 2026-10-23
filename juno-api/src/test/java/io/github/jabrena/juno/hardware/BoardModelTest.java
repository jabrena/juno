package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.internal.NativeBoardModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("BoardModel")
class BoardModelTest {
    @Test
    @DisplayName("The board's model is one shared instance")
    void shouldHandOutOneInstanceForTheBoard() {
        // When & Then
        assertThat(ArduinoOne.getBoardModel()).isSameAs(ArduinoOne.getBoardModel()).isSameAs(NativeBoardModel.INSTANCE);
    }

    @Test
    @DisplayName("A whole-program emulator can replace the board's model")
    void shouldHandOutTheReplacementBoardModel() {
        // Given
        BoardModel unoQ = mock(BoardModel.class);
        when(unoQ.isQ()).thenReturn(true);

        // When
        NativeBoardModel.setBoardModel(unoQ);

        // Then
        try {
            assertThat(ArduinoOne.getBoardModel()).isSameAs(unoQ);
            assertThat(ArduinoOne.getBoardModel().isQ()).isTrue();
        } finally {
            NativeBoardModel.setBoardModel(NativeBoardModel.INSTANCE);
        }
    }
}
