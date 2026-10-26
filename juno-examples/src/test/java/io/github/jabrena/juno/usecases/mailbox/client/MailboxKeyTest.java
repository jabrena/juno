package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.device.sd.EmulatedSdCard;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MailboxKeyTest {
    private final EmulatedSdCard card = new EmulatedSdCard();

    @Test
    void sendsTheBoardsDefaultKeyUntilOneIsTyped() {
        // Given
        card.insert();
        MailboxKey key = new MailboxKey(card);

        // When
        key.begin();

        // Then
        assertThat(header(key)).isEqualTo("Mailbox-API-Key: 1234567890\r\n");
    }

    @Test
    void keepsATypedKeyOnTheSdCardAcrossARestart() {
        // Given
        card.insert();
        MailboxKey key = new MailboxKey(card);
        key.begin();
        char[] typed = "24680135".toCharArray();

        // When
        boolean stored = key.change(typed, typed.length);
        MailboxKey afterRestart = new MailboxKey(card);
        afterRestart.begin();

        // Then
        assertThat(stored).isTrue();
        assertThat(header(key)).isEqualTo("Mailbox-API-Key: 24680135\r\n");
        assertThat(header(afterRestart)).isEqualTo("Mailbox-API-Key: 24680135\r\n");
        char[] copy = new char[MailboxKey.MAX_KEY_CHARS];
        assertThat(new String(copy, 0, afterRestart.copy(copy))).isEqualTo("24680135");
    }

    @Test
    void usesATypedKeyEvenWithoutAnSdCard() {
        // Given no card is inserted
        MailboxKey key = new MailboxKey(card);
        key.begin();
        char[] typed = "abcdef".toCharArray();

        // When
        boolean stored = key.change(typed, typed.length);

        // Then
        assertThat(stored).isFalse();
        assertThat(header(key)).isEqualTo("Mailbox-API-Key: abcdef\r\n");
    }

    private String header(MailboxKey key) {
        return new String(key.header(), 0, key.headerLength(), StandardCharsets.US_ASCII);
    }
}
