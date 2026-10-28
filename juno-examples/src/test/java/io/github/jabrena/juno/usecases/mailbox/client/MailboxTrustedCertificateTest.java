package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.device.sd.EmulatedSdCard;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MailboxTrustedCertificateTest {
    private static final String CERTIFICATE = "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----\n";

    private final EmulatedSdCard card = new EmulatedSdCard();
    private final MailboxTrustedCertificate certificate = new MailboxTrustedCertificate(card);

    @Test
    void loadsTheCaCertificateFromTheSdCard() {
        // Given
        card.insert();
        card.appendText("mailbox-ca.crt", CERTIFICATE);

        // When
        boolean loaded = certificate.load();

        // Then
        assertThat(loaded).isTrue();
        assertThat(text(certificate.value(), certificate.length())).isEqualTo(CERTIFICATE);
        assertThat(certificate.value()[certificate.length()]).isZero();
    }

    @Test
    void failsWhenTheCaCertificateIsMissing() {
        // Given an inserted card without the trust file
        card.insert();

        // When / Then
        assertThat(certificate.load()).isFalse();
    }

    @Test
    void failsWhenTheCaCertificateDoesNotLeaveRoomForTheTrailingNul() {
        // Given
        card.insert();
        card.appendText("mailbox-ca.crt", "x".repeat(MailboxTrustedCertificate.CAPACITY));

        // When / Then
        assertThat(certificate.load()).isFalse();
    }

    private static String text(byte[] buffer, int length) {
        return new String(buffer, 0, length, StandardCharsets.US_ASCII);
    }
}
