package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.BoardModel;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.security.SecretStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
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

}
