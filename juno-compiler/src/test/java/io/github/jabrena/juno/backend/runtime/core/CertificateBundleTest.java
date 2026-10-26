package io.github.jabrena.juno.backend.runtime.core;

import io.github.jabrena.juno.backend.runtime.RuntimeSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ESP32 trusted-root certificate bundle")
class CertificateBundleTest {
    private static final String PEM = new RuntimeSource().load("esp32/ca-certificates.pem");

    @Test
    @DisplayName("Bundles every root certificate of the shipped Mozilla list")
    void shouldBundleEveryShippedRoot() {
        // When
        CertificateBundle bundle = CertificateBundle.fromPem(PEM);

        // Then
        int certificates = PEM.split(Pattern.quote("-----BEGIN CERTIFICATE-----"), -1).length - 1;
        assertThat(bundle.size()).isEqualTo(certificates).isGreaterThan(100);
    }

    @Test
    @DisplayName("Writes the count, then each root's lengths, subject and key, sorted by subject for the binary search")
    void shouldWriteTheCoreBundleFormat() {
        // Given
        byte[] bytes = CertificateBundle.fromPem(PEM).bytes();

        // When
        List<byte[]> subjects = new ArrayList<>();
        int count = word(bytes, 0);
        int offset = 2;
        for (int index = 0; index < count; index++) {
            int subjectLength = word(bytes, offset);
            int keyLength = word(bytes, offset + 2);
            subjects.add(Arrays.copyOfRange(bytes, offset + 4, offset + 4 + subjectLength));
            offset += 4 + subjectLength + keyLength;
        }

        // Then
        assertThat(offset).isEqualTo(bytes.length);
        for (int index = 1; index < subjects.size(); index++) {
            assertThat(Arrays.compareUnsigned(subjects.get(index - 1), subjects.get(index))).isLessThanOrEqualTo(0);
        }
        assertThat(subjects).anySatisfy(subject ->
                assertThat(new String(subject, StandardCharsets.ISO_8859_1)).contains("ISRG Root X1"));
    }

    @Test
    @DisplayName("Ignores the text between certificates, as in curl's extract")
    void shouldIgnoreTextBetweenCertificates() {
        // Given
        String first = PEM.substring(PEM.indexOf("-----BEGIN CERTIFICATE-----"),
                PEM.indexOf("-----END CERTIFICATE-----") + "-----END CERTIFICATE-----".length());

        // When
        CertificateBundle bundle = CertificateBundle.fromPem("## header\nSome CA\n=======\n" + first + "\n# trailer\n");

        // Then
        assertThat(bundle.size()).isEqualTo(1);
        assertThat(bundle.cArray("juno_ca_bundle")).startsWith("static const uint8_t juno_ca_bundle[] = {\n  0x00, 0x01,")
                .endsWith("\n};\n");
    }

    @Test
    @DisplayName("Emits a custom trust anchor as PEM for the ESP32 client")
    void shouldEmitCustomPemTrustAnchor() {
        // Given
        String first = PEM.substring(PEM.indexOf("-----BEGIN CERTIFICATE-----"),
                PEM.indexOf("-----END CERTIFICATE-----") + "-----END CERTIFICATE-----".length());

        // When
        String source = CertificateBundle.cPem("juno_custom_ca", "comment\n" + first + "\n");

        // Then
        assertThat(source).startsWith("static const char juno_custom_ca[] = R\"JUNO_CA(\n")
                .contains(first)
                .endsWith(")JUNO_CA\";\n");
    }

    private static int word(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) << 8 | bytes[offset + 1] & 0xFF;
    }
}
