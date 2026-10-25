package io.github.jabrena.juno.hardware.device.sd;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class EmulatedSdCardTest {
    @Test
    void createsAndUsesAFileStemInsideADirectory() throws IOException {
        // Given
        EmulatedSdCard card = new EmulatedSdCard();
        card.insert();

        // When
        boolean directoryCreated = card.createDirectory("/mailbox/20261025");
        boolean written = card.appendText("/mailbox/20261025", "5500e840-e29b-41d4-a716-446655440000", "hello");

        // Then
        assertThat(directoryCreated).isTrue();
        assertThat(written).isTrue();
        assertThat(card.exists("/mailbox/20261025/5500e840-e29b-41d4-a716-446655440000.txt")).isTrue();
        assertThat(card.open("/mailbox/20261025", "5500e840-e29b-41d4-a716-446655440000"))
                .isNotNull();
    }

    @Test
    void listsDirectChildren() {
        // Given
        EmulatedSdCard card = new EmulatedSdCard();
        card.insert();
        card.createDirectory("/mailbox/20261025");
        card.appendText("/mailbox/20261025", "5500e840-e29b-41d4-a716-446655440000", "hello");
        byte[] name = new byte[48];

        // When
        int length = card.list("/mailbox", 0, name, name.length);

        // Then
        assertThat(new String(name, 0, length, java.nio.charset.StandardCharsets.UTF_8)).isEqualTo("20261025");
        assertThat(card.list("/mailbox", 1, name, name.length)).isZero();
    }
}
