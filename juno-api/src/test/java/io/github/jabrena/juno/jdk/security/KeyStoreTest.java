package io.github.jabrena.juno.jdk.security;

import io.github.jabrena.juno.jdk.security.cert.Certificate;
import io.github.jabrena.juno.jdk.security.cert.CertificateFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KeyStoreTest {
    private static final String PEM = "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----\n";

    @Test
    @DisplayName("a loaded store keeps every certificate added to it as one PEM chain")
    void keepsCertificatesAsOneChain() throws Exception {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        Certificate certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(PEM.getBytes(StandardCharsets.US_ASCII)));

        assertThat(store.pem()).isNull();
        store.setCertificateEntry("first", certificate);
        store.setCertificateEntry("second", certificate);

        assertThat(KeyStore.getDefaultType()).isEqualTo("pkcs12");
        assertThat(store.size()).isEqualTo(2);
        assertThat(store.pem()).isEqualTo(PEM + "\n" + PEM);
    }

    @Test
    @DisplayName("a store must be loaded empty first: a key store file cannot be read")
    void mustBeLoadedEmpty() throws Exception {
        KeyStore store = KeyStore.getInstance("jks");

        assertThatThrownBy(store::size).isInstanceOf(KeyStoreException.class).hasMessage("Uninitialized keystore");
        assertThatThrownBy(() -> store.load(new ByteArrayInputStream(new byte[1]), null))
                .isInstanceOf(IOException.class);
        assertThat(new GeneralSecurityException("x")).hasMessage("x");
    }
}
