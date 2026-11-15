package io.github.jabrena.juno.hardware.security;

import io.github.jabrena.juno.hardware.BoardModel;
import io.github.jabrena.juno.hardware.internal.NativeBoardModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretStoreFactoryTest {

    @AfterEach
    void resetBoard() {
        NativeBoardModel.setBoardModel(BoardModel.UNO_R4_WIFI);
    }

    @ParameterizedTest
    @EnumSource(value = BoardModel.class, names = {"PORTENTA_H7", "NANO_ESP32", "M5_CARDPUTER", "AI_THINKER_ESP32_CAM"})
    @DisplayName("the Portenta H7 and the ESP32 boards have a secret store")
    void storeBoardsHaveAStore(BoardModel board) {
        NativeBoardModel.setBoardModel(board);

        assertThat(SecretStoreFactory.isAvailable()).isTrue();
    }

    @ParameterizedTest
    @EnumSource(value = BoardModel.class, names = {"UNO_R3", "UNO_R4_WIFI", "UNO_Q", "UNO_R3_WIFI_REV2", "PICO_2_W"})
    @DisplayName("the other boards have no secret store, and opening one fails")
    void otherBoardsHaveNone(BoardModel board) {
        NativeBoardModel.setBoardModel(board);

        assertThat(SecretStoreFactory.isAvailable()).isFalse();
        assertThatThrownBy(() -> SecretStoreFactory.open(new SecretSlots()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("This board has no secret store");
    }
}
