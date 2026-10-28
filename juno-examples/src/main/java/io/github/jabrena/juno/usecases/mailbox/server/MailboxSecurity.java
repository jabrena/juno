package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.hardware.device.sd.SdCard;

import java.io.IOException;
import java.io.InputStream;

/**
 * The mailbox API key: the shared secret every {@code /api/v1/mailbox/} request carries in {@link #HEADER}. It is kept
 * in {@code /api-key.txt} on the SD card so it survives a reset, and starts as {@link #DEFAULT_KEY} until the Security
 * view changes it.
 */
public final class MailboxSecurity {
    /** The request header that carries the key. */
    static final String HEADER = "Mailbox-API-Key";
    static final String DEFAULT_KEY = "1234567890";
    /** The longest key, in bytes; the Security view's field holds this many digits. */
    static final int MAX_KEY_BYTES = 16;
    /** The shortest key the Security view accepts. */
    static final int MIN_KEY_BYTES = 6;
    private static final String KEY_FILE = "api-key.txt";

    private final SdCard sdCard;
    private final byte[] key = new byte[MAX_KEY_BYTES];
    // One byte over the longest key, so a longer header value is told apart from a key it starts with.
    private final byte[] candidate = new byte[MAX_KEY_BYTES + 1];
    private int keyLength;

    public MailboxSecurity(SdCard sdCard) {
        this.sdCard = sdCard;
    }

    /** Reads the stored key; a missing, empty or unreadable file leaves {@link #DEFAULT_KEY}. */
    synchronized void begin() {
        for (int i = 0; i < DEFAULT_KEY.length(); i++) {
            key[i] = (byte) DEFAULT_KEY.charAt(i);
        }
        keyLength = DEFAULT_KEY.length();
        int stored = readStoredKey();
        if (stored > 0) {
            for (int i = 0; i < stored; i++) {
                key[i] = candidate[i];
            }
            keyLength = stored;
        }
    }

    /** True while the key is still {@link #DEFAULT_KEY}, so the view can ask for it to be changed. */
    synchronized boolean isDefault() {
        if (keyLength != DEFAULT_KEY.length()) {
            return false;
        }
        for (int i = 0; i < keyLength; i++) {
            if (key[i] != DEFAULT_KEY.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /** Copies the key into {@code target}, which holds {@link #MAX_KEY_BYTES}, and returns its length. */
    synchronized int copyKey(byte[] target) {
        for (int i = 0; i < keyLength; i++) {
            target[i] = key[i];
        }
        return keyLength;
    }

    /** The buffer a request's header value is read into before {@link #matches(int)} checks it. */
    synchronized byte[] candidate() {
        return candidate;
    }

    /**
     * True when the first {@code length} bytes of {@link #candidate()} are the key; {@code length} is negative when
     * the request has no header. Every byte of both buffers is compared whatever the result, so the time taken does
     * not reveal how much of a guess was right.
     */
    synchronized boolean matches(int length) {
        int difference = length == keyLength ? 0 : 1;
        for (int i = 0; i < MAX_KEY_BYTES; i++) {
            int given = i < length ? candidate[i] : 0;
            int expected = i < keyLength ? key[i] : 0;
            difference |= given ^ expected;
        }
        return difference == 0;
    }

    /** Replaces the key with the first {@code length} bytes of {@code value} and stores it; false if not stored. */
    synchronized boolean change(byte[] value, int length) {
        if (length <= 0 || length > MAX_KEY_BYTES) {
            return false;
        }
        StringBuilder text = new StringBuilder(MAX_KEY_BYTES);
        for (int i = 0; i < length; i++) {
            text.append((char) value[i]);
        }
        sdCard.remove(KEY_FILE);
        if (!sdCard.appendText(KEY_FILE, text.toString())) {
            return false;
        }
        for (int i = 0; i < length; i++) {
            key[i] = value[i];
        }
        keyLength = length;
        return true;
    }

    /** Reads the stored key into {@link #candidate}, trimming spaces and line ends; -1 when there is none to use. */
    private int readStoredKey() {
        InputStream file = sdCard.open(KEY_FILE);
        if (file == null) {
            return -1;
        }
        int length = 0;
        try (InputStream openedFile = file) {
            while (openedFile.available() > 0) {
                int value = openedFile.read();
                if (value < 0 || length == MAX_KEY_BYTES && value > ' ') {
                    return -1;
                }
                if (value > ' ') {
                    candidate[length++] = (byte) value;
                }
            }
        } catch (IOException exception) {
            return -1;
        }
        return length;
    }
}
