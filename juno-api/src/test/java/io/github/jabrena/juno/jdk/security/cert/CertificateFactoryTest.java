package io.github.jabrena.juno.jdk.security.cert;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CertificateFactoryTest {
    static final String PEM = "-----BEGIN CERTIFICATE-----\n" + "MIIB".repeat(200) + "\n-----END CERTIFICATE-----\n";

    @Test
    @DisplayName("an X.509 factory reads a whole PEM stream, past its first capacity, into a certificate")
    void readsAPemCertificate() throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");

        Certificate certificate = factory.generateCertificate(stream(PEM + PEM));

        assertThat(factory.getType()).isEqualTo("X.509");
        assertThat(certificate.getType()).isEqualTo("X.509");
        assertThat(certificate.pem()).isEqualTo(PEM + PEM).hasSizeGreaterThan(1600);
    }

    @Test
    @DisplayName("a stream that reports nothing available is still read to its end")
    void readsAStreamWithoutAvailable() throws Exception {
        InputStream unknownSize = new ByteArrayInputStream(PEM.getBytes(StandardCharsets.US_ASCII)) {
            @Override
            public synchronized int available() {
                return 0;
            }
        };

        assertThat(CertificateFactory.getInstance("X.509").generateCertificate(unknownSize).pem()).isEqualTo(PEM);
    }

    @Test
    @DisplayName("another type, text without a certificate and an unreadable stream are rejected")
    void rejectsWhatIsNotAPemCertificate() throws Exception {
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("card removed");
            }
        };

        assertThatThrownBy(() -> CertificateFactory.getInstance("PKCS7")).isInstanceOf(CertificateException.class)
                .hasMessage("PKCS7 not found");
        assertThatThrownBy(() -> factory.generateCertificate(stream("hello"))).isInstanceOf(CertificateException.class);
        assertThatThrownBy(() -> factory.generateCertificate(broken)).isInstanceOf(CertificateException.class);
    }

    static InputStream stream(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII));
    }
}
