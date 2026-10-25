package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.hardware.device.sd.EmulatedSdCard;
import io.github.jabrena.juno.hardware.net.http.HttpStatus;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MailboxModelTest {
    @Test
    void restoresMessagesFromExistingSdFiles() {
        // Given
        EmulatedSdCard card = new EmulatedSdCard();
        card.insert();
        card.createDirectory("/mailbox/20261025100000");
        card.appendText("/mailbox/20261025100000", "5500e840-e29b-41d4-a716-446655440000",
                "{\"uuid\":\"5500e840-e29b-41d4-a716-446655440000\",\"title\":\"Old\","
                        + "\"body\":\"Message from a previous version\",\"createAt\":\"2026-10-25T10:00:00\"}");

        // When
        MailboxModel model = new MailboxModel(card);
        model.begin();

        // Then
        assertThat(model.messageCount()).isOne();
        StringBuilder response = new StringBuilder();
        assertThat(model.appendStoredMessage(response, 0)).isTrue();
        assertThat(response.toString()).contains("Message from a previous version");
    }

    @Test
    void storesTheRequestTitleAndBodyInATimestampFolder() {
        // Given
        EmulatedSdCard card = new EmulatedSdCard();
        card.insert();
        MailboxModel model = new MailboxModel(card);
        model.begin();
        byte[] request = "{\"title\":\"Hello\",\"body\":\"Detail of the message, longer than one string slot\"}"
                .getBytes(StandardCharsets.UTF_8);

        // When
        int status = model.createMessage(request, request.length);

        // Then
        assertThat(status).isEqualTo(HttpStatus.CREATED);
        StringBuilder response = new StringBuilder();
        assertThat(model.appendStoredMessage(response, 0)).isTrue();
        assertThat(response.toString())
                .contains("\"title\":\"Hello\"", "\"body\":\"Detail of the message, longer than one string slot\"");
        byte[] folder = new byte[48];
        int folderLength = card.list("/mailbox", 0, folder, folder.length);
        assertThat(new String(folder, 0, folderLength, StandardCharsets.UTF_8)).matches("20\\d{12}");
    }

    @Test
    void storesATweetWithNonAsciiCharacters() {
        // Given
        EmulatedSdCard card = new EmulatedSdCard();
        card.insert();
        MailboxModel model = new MailboxModel(card);
        model.begin();
        String tweet = "Configuración del buzón en la placa, ¡qué ilusión! ñandú";
        byte[] request = ("{\"title\":\"Tweet\",\"body\":\"" + tweet + "\"}").getBytes(StandardCharsets.UTF_8);

        // When
        int status = model.createMessage(request, request.length);

        // Then
        assertThat(status).isEqualTo(HttpStatus.CREATED);
        StringBuilder response = new StringBuilder();
        assertThat(model.appendStoredMessage(response, 0)).isTrue();
        // The board writes each UTF-8 byte as one char; the emulator encodes those chars as UTF-8 once more.
        byte[] stored = new byte[response.length()];
        for (int i = 0; i < stored.length; i++) {
            stored[i] = (byte) response.charAt(i);
        }
        String text = new String(stored, StandardCharsets.UTF_8);
        byte[] boardBytes = new byte[text.length()];
        for (int i = 0; i < boardBytes.length; i++) {
            boardBytes[i] = (byte) text.charAt(i);
        }
        assertThat(new String(boardBytes, StandardCharsets.UTF_8)).contains("\"body\":\"" + tweet + "\"");
    }

    @Test
    void rejectsABodyLongerThanTheLimit() {
        // Given
        EmulatedSdCard card = new EmulatedSdCard();
        card.insert();
        MailboxModel model = new MailboxModel(card);
        model.begin();
        byte[] request = ("{\"title\":\"Hello\",\"body\":\"" + "x".repeat(MailboxModel.MAX_BODY_BYTES + 1) + "\"}")
                .getBytes(StandardCharsets.UTF_8);

        // When
        int status = model.createMessage(request, request.length);

        // Then
        assertThat(status).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
        assertThat(model.messageCount()).isZero();
    }
}
