package io.github.jabrena.juno.usecases.mailbox.server.services;

import io.github.jabrena.juno.hardware.device.sd.SdCard;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * The HTTPS identity, read from {@code /server.crt} and {@code /server.key} (PEM) on the SD card at startup, so a
 * certificate is replaced by swapping two files instead of rebuilding the firmware. The key may be SEC1
 * ({@code BEGIN EC PRIVATE KEY}) or PKCS#8 ({@code BEGIN PRIVATE KEY}). The pair goes into an in-memory key store and
 * out as an {@link SSLContext}, the JDK way; the server wipes the key once it has parsed it.
 */
public final class MailboxCredentials {
    static final String CERTIFICATE_FILE = "server.crt";
    static final String PRIVATE_KEY_FILE = "server.key";
    /** Large enough for an EC P-256 or RSA-2048 private key in PEM. */
    static final int PEM_CAPACITY = 2048;
    private final SdCard sdCard;

    public MailboxCredentials(SdCard sdCard) {
        this.sdCard = sdCard;
    }

    /** The server's SSL context; {@code null} when either file is missing, unreadable or does not parse. */
    SSLContext load() {
        try {
            Certificate certificate = loadCertificate();
            PrivateKey privateKey = loadPrivateKey();
            if (certificate == null || privateKey == null) {
                return null;
            }
            // This store exists only in memory; no password file is needed.
            char[] password = new char[0];
            KeyStore identity = KeyStore.getInstance("PKCS12");
            identity.load(null, password);
            identity.setKeyEntry("server", privateKey, password, new Certificate[]{certificate});
            KeyManagerFactory managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            managers.init(identity, password);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(managers.getKeyManagers(), null, null);
            return context;
        } catch (IOException | GeneralSecurityException exception) {
            return null;
        }
    }

    private Certificate loadCertificate() throws IOException, GeneralSecurityException {
        try (InputStream input = sdCard.open(CERTIFICATE_FILE)) {
            if (input == null) {
                return null;
            }
            return CertificateFactory.getInstance("X.509").generateCertificate(input);
        }
    }

    private PrivateKey loadPrivateKey() throws IOException, GeneralSecurityException {
        String base64;
        try (InputStream input = sdCard.open(PRIVATE_KEY_FILE)) {
            if (input == null) {
                return null;
            }
            base64 = readPemBody(input);
        }
        byte[] der = Base64.getMimeDecoder().decode(base64);
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    /** The Base64 lines of a PEM file, without its {@code -----BEGIN/END} lines or line breaks. */
    private static String readPemBody(InputStream input) throws IOException {
        StringBuilder pem = new StringBuilder(PEM_CAPACITY);
        int pemLength = 0;
        boolean skipLine = false;
        boolean lineStart = true;
        int value;
        while ((value = input.read()) >= 0) {
            if (value == '\n') {
                skipLine = false;
                lineStart = true;
            } else if (lineStart && value == '-') {
                skipLine = true;
                lineStart = false;
            } else if (!skipLine && value != '\r') {
                if (pemLength == PEM_CAPACITY) {
                    throw new IOException("server.key exceeds PEM_CAPACITY");
                }
                pem.append((char) value);
                pemLength++;
                lineStart = false;
            }
        }
        return pem.toString();
    }
}
