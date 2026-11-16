package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.BoardModel;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.security.SecretStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class NativeSecretStoreTest {

    @AfterEach
    void reset() {
        NativeSecretStore.setSecretStore(null);
        NativeBoardModel.setBoardModel(BoardModel.UNO_R4_WIFI);
    }

    @Test
    @DisplayName("MCU.getSecretStore() hands out one shared store")
    void shouldHandOutOneSharedStore() {
        // Given
        SecretStore store = mock(SecretStore.class);
        NativeSecretStore.setSecretStore(store);

        // When & Then
        assertThat(MCU.getSecretStore()).isSameAs(store).isSameAs(MCU.getSecretStore());
    }

    @ParameterizedTest
    @EnumSource(value = BoardModel.class, names = {"UNO_R3", "UNO_Q", "UNO_R3_WIFI_REV2"})
    @DisplayName("a board without a secret store fails to open one")
    void otherBoardsHaveNone(BoardModel board) {
        // Given
        NativeBoardModel.setBoardModel(board);

        // When & Then
        assertThatThrownBy(MCU::getSecretStore)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("This board has no secret store");
    }
}
