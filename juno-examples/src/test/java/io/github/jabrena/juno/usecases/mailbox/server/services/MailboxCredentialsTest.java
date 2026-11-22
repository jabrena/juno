package io.github.jabrena.juno.usecases.mailbox.server.services;

import io.github.jabrena.juno.hardware.device.sd.EmulatedSdCard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailboxCredentialsTest {
    private static final String CERTIFICATE = "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----\n";
    // Not a real key: a real-looking header would trip secret scanners, and it must not parse.
    private static final String KEY = "-----BEGIN TEST KEY-----\nAAAA\n-----END TEST KEY-----\n";

    private final EmulatedSdCard card = new EmulatedSdCard();
    private final MailboxCredentials credentials = new MailboxCredentials(card);

    @Test
    void failsWhenTheCertificateIsMissing() {
        // Given only the key
        card.insert();
        card.appendText("server.key", KEY);

        // When / Then
        assertThat(credentials.load()).isNull();
    }

    @Test
    void failsWhenTheKeyIsMissing() {
        // Given only the certificate
        card.insert();
        card.appendText("server.crt", CERTIFICATE);

        // When / Then
        assertThat(credentials.load()).isNull();
    }

    @Test
    void failsWhenTheFilesDoNotParse() {
        // Given a certificate and key that are not valid PEM
        card.insert();
        card.appendText("server.crt", CERTIFICATE);
        card.appendText("server.key", KEY);

        // When / Then
        assertThat(credentials.load()).isNull();
    }
}
