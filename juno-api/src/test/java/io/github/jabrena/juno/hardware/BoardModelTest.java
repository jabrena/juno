package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.internal.NativeBoardModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BoardModel")
class BoardModelTest {

    @AfterEach
    void restoreDefault() {
        NativeBoardModel.setBoardModel(BoardModel.UNO_R4_WIFI);
    }

    @Test
    @DisplayName("The board answers with one constant, so asking twice gives the same one")
    void shouldAnswerWithOneConstant() {
        // When & Then
        assertThat(MCU.getBoardModel()).isSameAs(MCU.getBoardModel()).isInstanceOf(BoardModel.class);
    }

    @Test
    @DisplayName("A whole-program emulator can say which board it is pretending to be")
    void shouldHandOutTheReplacementBoardModel() {
        // When
        NativeBoardModel.setBoardModel(BoardModel.PORTENTA_H7);

        // Then
        assertThat(MCU.getBoardModel()).isEqualTo(BoardModel.PORTENTA_H7);
    }

    @Test
    @DisplayName("A switch over the model picks one arm per board, which is what the compiler folds")
    void shouldSwitchOverTheModel() {
        // Given
        NativeBoardModel.setBoardModel(BoardModel.PORTENTA_H7);

        // When
        String chosen = switch (MCU.getBoardModel()) {
            case PORTENTA_H7 -> "se050";
            case NANO_ESP32, M5_CARDPUTER, AI_THINKER_ESP32_CAM -> "nvs";
            default -> "none";
        };

        // Then
        assertThat(chosen).isEqualTo("se050");
    }
}
