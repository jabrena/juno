package io.github.jabrena.juno.jdk.security;

import io.github.jabrena.juno.jdk.security.cert.Certificate;
import io.github.jabrena.juno.jdk.security.cert.CertificateFactory;
import io.github.jabrena.juno.jdk.security.spec.KeySpec;
import io.github.jabrena.juno.jdk.security.spec.PKCS8EncodedKeySpec;
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

    @Test
    void validatesAndExposesAnEcServerIdentity() throws Exception {
        KeyStore store = KeyStore.getInstance("PKCS12");
        assertThat(store.serverPrivateKey()).isNull();
        assertThat(store.serverCertificate()).isNull();
        store.load(null, new char[0]);
        byte[] der = {1, 2, 3};
        PrivateKey privateKey = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        Certificate certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(PEM.getBytes(StandardCharsets.US_ASCII)));

        assertThatThrownBy(() -> store.setKeyEntry("server", privateKey, new char[0], new Certificate[0]))
                .isInstanceOf(KeyStoreException.class);
        store.setKeyEntry("server", privateKey, new char[0], new Certificate[]{certificate});
        assertThat(store.serverPrivateKey()).isEqualTo(der);
        assertThat(store.serverCertificate()).isEqualTo(PEM);
        assertThat(store.size()).isEqualTo(1);
        assertThatThrownBy(() -> KeyFactory.getInstance("RSA")).isInstanceOf(NoSuchAlgorithmException.class);
        assertThatThrownBy(() -> KeyFactory.getInstance("EC").generatePrivate(new KeySpec() { }))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(KeyFactory.encodedLength(KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(new byte[] {0x30, 0x03, 1, 2, 3})))).isEqualTo(5);
        assertThat(KeyFactory.encodedLength(KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(new byte[] {0x30, (byte) 0x82, 0, 3, 1, 2, 3})))).isEqualTo(7);

        KeyStore empty = KeyStore.getInstance("PKCS12");
        empty.load(null, null);
        assertThatThrownBy(() -> io.github.jabrena.juno.javax.net.ssl.KeyManagerFactory.getInstance("SunX509")
                .init(empty, new char[0])).isInstanceOf(IllegalArgumentException.class);
    }
}
