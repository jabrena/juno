package io.github.jabrena.juno.usecases.mailbox.server;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailboxStartupStateTest {
    @Test
    void startsWhileTheStorageTaskIsRunning() {
        // Given
        MailboxStartupState state = new MailboxStartupState();

        // Then
        assertThat(state.status()).isEqualTo(MailboxStartupState.STARTING);
    }

    @Test
    void publishesReadyAfterTheBootstrapTaskCompletes() {
        // Given
        MailboxStartupState state = new MailboxStartupState();

        // When
        state.ready();

        // Then
        assertThat(state.status()).isEqualTo(MailboxStartupState.READY);
    }

    @Test
    void publishesFailedWhenStorageOrTlsCannotStart() {
        // Given
        MailboxStartupState state = new MailboxStartupState();

        // When
        state.failed();

        // Then
        assertThat(state.status()).isEqualTo(MailboxStartupState.FAILED);
    }
}
