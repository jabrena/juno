package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.hardware.device.sd.EmulatedSdCard;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class MailboxCredentialsTest {
    private static final String CERTIFICATE = "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----\n";
    // Not a real key: the loader only copies bytes, and a real-looking header would trip secret scanners.
    private static final String KEY = "-----BEGIN TEST KEY-----\nAAAA\n-----END TEST KEY-----\n";

    private final EmulatedSdCard card = new EmulatedSdCard();
    private final MailboxCredentials credentials = new MailboxCredentials(card);

    @Test
    void loadsTheCertificateAndKeyFromTheSdCard() {
        // Given
        card.insert();
        card.appendText("server.crt", CERTIFICATE);
        card.appendText("server.key", KEY);

        // When
        boolean loaded = credentials.load();

        // Then
        assertThat(loaded).isTrue();
        assertThat(text(credentials.certificate(), credentials.certificateLength())).isEqualTo(CERTIFICATE);
        assertThat(text(credentials.privateKey(), credentials.privateKeyLength())).isEqualTo(KEY);
    }

    @Test
    void failsWhenEitherFileIsMissing() {
        // Given only the certificate
        card.insert();
        card.appendText("server.crt", CERTIFICATE);

        // When / Then
        assertThat(credentials.load()).isFalse();
    }

    @Test
    void failsWhenAFileIsLargerThanItsBuffer() {
        // Given
        card.insert();
        card.appendText("server.crt", "x".repeat(MailboxCredentials.PEM_CAPACITY + 1));
        card.appendText("server.key", KEY);

        // When / Then
        assertThat(credentials.load()).isFalse();
    }

    @Test
    void wipesThePrivateKeyOnceTheServerHasParsedIt() {
        // Given
        card.insert();
        card.appendText("server.crt", CERTIFICATE);
        card.appendText("server.key", KEY);
        credentials.load();

        // When
        credentials.wipePrivateKey();

        // Then
        assertThat(credentials.privateKeyLength()).isZero();
        for (int i = 0; i < KEY.length(); i++) {
            assertThat(credentials.privateKey()[i]).isZero();
        }
    }

    private static String text(byte[] buffer, int length) {
        return new String(buffer, 0, length, StandardCharsets.US_ASCII);
    }
}
