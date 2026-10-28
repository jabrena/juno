package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.device.sd.SdCard;

import java.io.IOException;
import java.io.InputStream;

/** Loads the mailbox CA certificate from the Cardputer's SD card into one persistent, NUL-terminated buffer. */
final class MailboxTrustedCertificate {
    static final String FILE = "mailbox-ca.crt";
    /** Large enough for the generated development CA certificate, including its trailing NUL. */
    static final int CAPACITY = 2048;
    private final SdCard sdCard;
    private final byte[] certificate = new byte[CAPACITY];
    private int length;

    MailboxTrustedCertificate(SdCard sdCard) {
        this.sdCard = sdCard;
    }

    /** Reads the PEM certificate; false when it is missing, empty, unreadable or does not fit with a trailing NUL. */
    boolean load() {
        length = 0;
        try (InputStream file = sdCard.open(FILE)) {
            if (file == null) {
                return false;
            }
            while (true) {
                if (length == certificate.length - 1) {
                    if (file.available() > 0) {
                        return false;
                    }
                    break;
                }
                int count = file.read(certificate, length, certificate.length - 1 - length);
                if (count < 0) {
                    break;
                }
                if (count == 0) {
                    return false;
                }
                length += count;
            }
            certificate[length] = 0;
            return length > 0;
        } catch (IOException exception) {
            return false;
        }
    }

    byte[] value() {
        return certificate;
    }

    int length() {
        return length;
    }
}
