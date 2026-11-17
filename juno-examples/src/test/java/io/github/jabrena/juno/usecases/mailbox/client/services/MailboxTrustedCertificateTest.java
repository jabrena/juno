package io.github.jabrena.juno.usecases.mailbox.client.services;

import io.github.jabrena.juno.hardware.device.sd.EmulatedSdCard;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MailboxTrustedCertificateTest {
    /** A throwaway self-signed test CA, with no private key anywhere: it only has to parse. */
    private static final String CERTIFICATE = """
            -----BEGIN CERTIFICATE-----
            MIIBkzCCATmgAwIBAgIUNGaKlwi2BLapsLElpLw2XlvpNPUwCgYIKoZIzj0EAwIw
            HzEdMBsGA1UEAwwUSnVubyBNYWlsYm94IFRlc3QgQ0EwHhcNMjYxMTE3MDIzNzE0
            WhcNMzYxMTE0MDIzNzE0WjAfMR0wGwYDVQQDDBRKdW5vIE1haWxib3ggVGVzdCBD
            QTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABL+y2303TndNdpO1a1VJ4aGYoN0V
            udeUzNpYP7ff75bbG21iCNzt5d+bSJ0f6TVLu3t+C1xVSg2NStxINoqCxI6jUzBR
            MB0GA1UdDgQWBBSPH5Lb8WyHfA11eTbVD3gWw9RcfTAfBgNVHSMEGDAWgBSPH5Lb
            8WyHfA11eTbVD3gWw9RcfTAPBgNVHRMBAf8EBTADAQH/MAoGCCqGSM49BAMCA0gA
            MEUCIQDCn0SVn/VnLM59c4cSvYLDK8K4JFWVYTjx66bKXPMhcgIgFh/K6wpOXEAW
            6qC7x4xv2tIK1F6xn/2l4WA8OSJzDdM=
            -----END CERTIFICATE-----
            """;

    private final EmulatedSdCard card = new EmulatedSdCard();
    private final MailboxTrustedCertificate certificate = new MailboxTrustedCertificate(card);

    @Test
    void loadsTheCaCertificateFromTheSdCardIntoAnSslContext() {
        // Given
        card.insert();
        card.appendText("mailbox-ca.crt", CERTIFICATE);

        // When
        boolean loaded = certificate.load();

        // Then
        assertThat(loaded).isTrue();
        assertThat(certificate.context()).isNotNull();
        assertThat(certificate.context().getProtocol()).isEqualTo("TLS");
    }

    @Test
    void failsWhenTheCaCertificateIsMissing() {
        // Given an inserted card without the trust file
        card.insert();

        // When / Then
        assertThat(certificate.load()).isFalse();
        assertThat(certificate.context()).isNull();
    }

    @Test
    void failsWhenTheFileIsNotACertificate() {
        // Given
        card.insert();
        card.appendText("mailbox-ca.crt", "not a certificate");

        // When / Then
        assertThat(certificate.load()).isFalse();
    }
}
