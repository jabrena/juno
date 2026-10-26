package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.hardware.device.sd.EmulatedSdCard;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MailboxSecurityTest {
    private final EmulatedSdCard card = new EmulatedSdCard();

    @Test
    void startsWithTheDefaultKeyWhenTheSdCardHasNone() {
        // Given
        card.insert();
        MailboxSecurity security = new MailboxSecurity(card);

        // When
        security.begin();

        // Then
        assertThat(security.isDefault()).isTrue();
        assertThat(matches(security, "1234567890")).isTrue();
    }

    @Test
    void rejectsAMissingWrongLongerOrShorterKey() {
        // Given
        card.insert();
        MailboxSecurity security = new MailboxSecurity(card);
        security.begin();

        // When / Then
        assertThat(security.matches(-1)).isFalse();
        assertThat(matches(security, "")).isFalse();
        assertThat(matches(security, "1234567891")).isFalse();
        assertThat(matches(security, "123456789")).isFalse();
        assertThat(matches(security, "12345678901")).isFalse();
    }

    @Test
    void keepsAChangedKeyOnTheSdCardAcrossAReset() {
        // Given
        card.insert();
        MailboxSecurity security = new MailboxSecurity(card);
        security.begin();
        byte[] newKey = "24680135".getBytes(StandardCharsets.US_ASCII);

        // When
        boolean changed = security.change(newKey, newKey.length);
        MailboxSecurity afterReset = new MailboxSecurity(card);
        afterReset.begin();

        // Then
        assertThat(changed).isTrue();
        assertThat(afterReset.isDefault()).isFalse();
        assertThat(matches(afterReset, "24680135")).isTrue();
        assertThat(matches(afterReset, "1234567890")).isFalse();
    }

    @Test
    void readsAKeyEditedOnAComputerIgnoringItsLineEnd() {
        // Given
        card.insert();
        card.appendText("api-key.txt", "secret-key\r\n");
        MailboxSecurity security = new MailboxSecurity(card);

        // When
        security.begin();

        // Then
        assertThat(matches(security, "secret-key")).isTrue();
    }

    @Test
    void refusesAnEmptyOrTooLongKey() {
        // Given
        card.insert();
        MailboxSecurity security = new MailboxSecurity(card);
        security.begin();
        byte[] tooLong = "12345678901234567".getBytes(StandardCharsets.US_ASCII);

        // When / Then
        assertThat(security.change(tooLong, 0)).isFalse();
        assertThat(security.change(tooLong, tooLong.length)).isFalse();
        assertThat(security.isDefault()).isTrue();
    }

    private boolean matches(MailboxSecurity security, String header) {
        byte[] value = header.getBytes(StandardCharsets.US_ASCII);
        byte[] candidate = security.candidate();
        int length = Math.min(value.length, candidate.length);
        System.arraycopy(value, 0, candidate, 0, length);
        return security.matches(length);
    }
}
