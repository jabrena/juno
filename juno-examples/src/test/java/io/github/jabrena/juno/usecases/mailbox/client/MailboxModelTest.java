package io.github.jabrena.juno.usecases.mailbox.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MailboxModelTest {
    private static final byte[] FIRST_UUID = bytes("6f0a6a4b-36b8-4dc9-bc67-8f4a3bfb2d61");
    private static final byte[] SECOND_UUID = bytes("1c2b6a4b-36b8-4dc9-bc67-8f4a3bfb2d62");

    @Test
    void exposesAllMessagesToTheReusableOptionList() {
        // Given
        MailboxModel model = new MailboxModel();
        add(model, FIRST_UUID, "First message");
        add(model, SECOND_UUID, "Second message");

        // Then
        assertThat(model.count()).isEqualTo(2);
        assertThat(model.labelLength(0)).isEqualTo("First message".length());
        assertThat(model.titleCharacter(1, 0)).isEqualTo('S');
    }

    @Test
    void findsAMessageByUuidAfterTheInboxIsReordered() {
        // Given
        MailboxModel model = new MailboxModel();
        add(model, FIRST_UUID, "First message");
        add(model, SECOND_UUID, "Second message");

        // When
        model.clear();
        add(model, SECOND_UUID, "Second message");

        // Then
        assertThat(model.indexOf(SECOND_UUID, SECOND_UUID.length)).isZero();
        assertThat(model.indexOf(FIRST_UUID, FIRST_UUID.length)).isEqualTo(-1);
    }

    @Test
    void clearsTheBoundedInboxBeforeARefresh() {
        // Given
        MailboxModel model = new MailboxModel();
        add(model, FIRST_UUID, "First message");

        // When
        model.clear();

        // Then
        assertThat(model.messageCount()).isZero();
        assertThat(model.revision()).isOne();
    }

    private static void add(MailboxModel model, byte[] uuid, String title) {
        byte[] titleBytes = bytes(title);
        System.arraycopy(titleBytes, 0, model.title(model.messageCount()), 0, titleBytes.length);
        model.add(uuid, uuid.length, titleBytes.length, 0);
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }
}
