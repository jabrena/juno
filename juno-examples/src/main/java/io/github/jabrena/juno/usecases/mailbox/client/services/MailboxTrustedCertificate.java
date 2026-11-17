package io.github.jabrena.juno.usecases.mailbox.client.services;

import io.github.jabrena.juno.hardware.device.sd.SdCard;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

/**
 * Loads the mailbox CA certificate from the Cardputer's SD card into an {@link SSLContext} that trusts only it, the
 * JDK way: a {@link KeyStore} holding the certificate, through a {@link TrustManagerFactory}.
 */
public final class MailboxTrustedCertificate {
    static final String FILE = "mailbox-ca.crt";
    private final SdCard sdCard;
    private SSLContext context;

    public MailboxTrustedCertificate(SdCard sdCard) {
        this.sdCard = sdCard;
    }

    /** Reads the PEM certificate; false when it is missing, unreadable or not a certificate. */
    public boolean load() {
        try (InputStream file = sdCard.open(FILE)) {
            if (file == null) {
                return false;
            }
            KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
            store.load(null, null);
            store.setCertificateEntry("mailbox", CertificateFactory.getInstance("X.509").generateCertificate(file));
            TrustManagerFactory trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trust.init(store);
            SSLContext loaded = SSLContext.getInstance("TLS");
            loaded.init(null, trust.getTrustManagers(), null);
            context = loaded;
            return true;
        } catch (IOException | GeneralSecurityException exception) {
            return false;
        }
    }

    /** The context that trusts the mailbox CA, or {@code null} before a successful {@link #load()}. */
    public SSLContext context() {
        return context;
    }
}
