package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.hardware.device.sd.SdCard;

import java.io.IOException;
import java.io.InputStream;

/**
 * The HTTPS certificate and private key, read as PEM text from {@code /server.crt} and {@code /server.key} on the SD
 * card at startup, so a certificate is replaced by swapping two files instead of rebuilding the firmware. Both buffers
 * are allocated once; the key is wiped as soon as the server has parsed it.
 */
public final class MailboxCredentials {
    static final String CERTIFICATE_FILE = "server.crt";
    static final String PRIVATE_KEY_FILE = "server.key";
    /** Large enough for an EC P-256 or RSA-2048 certificate or key in PEM. */
    static final int PEM_CAPACITY = 2048;
    private final SdCard sdCard;
    private final byte[] certificate = new byte[PEM_CAPACITY];
    private final byte[] privateKey = new byte[PEM_CAPACITY];
    private int certificateLength;
    private int privateKeyLength;

    public MailboxCredentials(SdCard sdCard) {
        this.sdCard = sdCard;
    }

    /** Reads both files; false when either is missing, empty, unreadable or larger than {@link #PEM_CAPACITY}. */
    boolean load() {
        certificateLength = read(false, certificate);
        privateKeyLength = read(true, privateKey);
        return certificateLength > 0 && privateKeyLength > 0;
    }

    byte[] certificate() {
        return certificate;
    }

    int certificateLength() {
        return certificateLength;
    }

    byte[] privateKey() {
        return privateKey;
    }

    int privateKeyLength() {
        return privateKeyLength;
    }

    /** Overwrites the key; the TLS server keeps its own parsed copy, so the program no longer needs it. */
    void wipePrivateKey() {
        for (int i = 0; i < privateKeyLength; i++) {
            privateKey[i] = 0;
        }
        privateKeyLength = 0;
    }

    /**
     * Copies the key or the certificate into {@code target}; its length, or {@code -1} when the file is missing,
     * unreadable or too large. Juno resolves an SD path at compile time, so each file is opened by its own constant.
     */
    private int read(boolean key, byte[] target) {
        InputStream file = key ? sdCard.open(PRIVATE_KEY_FILE) : sdCard.open(CERTIFICATE_FILE);
        if (file == null) {
            return -1;
        }
        int length = 0;
        try (InputStream openedFile = file) {
            while (true) {
                if (length == target.length) {
                    return openedFile.available() == 0 ? length : -1;
                }
                int count = openedFile.read(target, length, target.length - length);
                if (count < 0) {
                    break;
                }
                if (count == 0) {
                    return -1;
                }
                length += count;
            }
        } catch (IOException exception) {
            return -1;
        }
        return length;
    }
}
