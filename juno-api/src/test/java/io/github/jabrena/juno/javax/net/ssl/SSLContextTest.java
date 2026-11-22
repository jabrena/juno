package io.github.jabrena.juno.javax.net.ssl;

import io.github.jabrena.juno.jdk.security.KeyFactory;
import io.github.jabrena.juno.jdk.security.KeyManagementException;
import io.github.jabrena.juno.jdk.security.KeyStore;
import io.github.jabrena.juno.jdk.security.NoSuchAlgorithmException;
import io.github.jabrena.juno.jdk.security.cert.Certificate;
import io.github.jabrena.juno.jdk.security.cert.CertificateFactory;
import io.github.jabrena.juno.jdk.security.spec.PKCS8EncodedKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SSLContextTest {
    private static final String PEM = "-----BEGIN CERTIFICATE-----\nMIIB\n-----END CERTIFICATE-----\n";

    @Test
    void carriesAnEcServerIdentityFromTheJdkKeyRecipe() throws Exception {
        byte[] der = {1, 2, 3};
        KeyStore store = KeyStore.getInstance("PKCS12");
        store.load(null, new char[0]);
        Certificate certificate = CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(PEM.getBytes(StandardCharsets.US_ASCII)));
        store.setKeyEntry("server", KeyFactory.getInstance("EC")
                .generatePrivate(new PKCS8EncodedKeySpec(der)), new char[0], new Certificate[]{certificate});
        KeyManagerFactory factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        factory.init(store, new char[0]);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(factory.getKeyManagers(), null, null);

        assertThat(context.serverCertificate()).isEqualTo(PEM);
        assertThat(context.serverPrivateKey()).isEqualTo(der);
    }

    @Test
    @DisplayName("the JDK's trust-my-CA recipe gives a context that trusts that CA")
    void trustsTheCertificatesOfItsTrustManagers() throws Exception {
        KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
        store.load(null, null);
        store.setCertificateEntry("ca", CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(PEM.getBytes(StandardCharsets.US_ASCII))));
        TrustManagerFactory factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        factory.init(store);
        SSLContext context = SSLContext.getInstance("TLS");

        context.init(null, factory.getTrustManagers(), null);

        assertThat(factory.getAlgorithm()).isEqualTo("PKIX");
        assertThat(context.getProtocol()).isEqualTo("TLS");
        assertThat(context.trustedCertificates()).isEqualTo(PEM);
    }

    @Test
    @DisplayName("no trust managers, a null key store and the default context trust the board's own roots")
    void trustsTheBoardByDefault() throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance("PKIX");
        factory.init(null);
        SSLContext fromNullStore = SSLContext.getInstance("TLSv1.2");
        fromNullStore.init(null, factory.getTrustManagers(), null);
        SSLContext withoutManagers = SSLContext.getInstance("TLSv1.3");
        withoutManagers.init(new KeyManager[0], new TrustManager[] {new TrustManager() { }}, null);

        assertThat(fromNullStore.trustedCertificates()).isNull();
        assertThat(withoutManagers.trustedCertificates()).isNull();
        assertThat(SSLContext.getDefault()).isSameAs(SSLContext.boardDefault());
        assertThat(SSLContext.getDefault().trustedCertificates()).isNull();
        assertThat(SSLContext.getInstance("Default").getProtocol()).isEqualTo("Default");
    }

    @Test
    @DisplayName("an unknown protocol, client certificates and an uninitialised context or factory are rejected")
    void rejectsWhatTheBoardCannotDo() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");

        assertThatThrownBy(() -> SSLContext.getInstance("SSLv3")).isInstanceOf(NoSuchAlgorithmException.class);
        assertThatThrownBy(() -> context.init(new KeyManager[] {new KeyManager() { }}, null, null))
                .isInstanceOf(KeyManagementException.class);
        assertThatThrownBy(context::trustedCertificates).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> TrustManagerFactory.getInstance("PKIX").getTrustManagers())
                .isInstanceOf(IllegalStateException.class);
    }
}
