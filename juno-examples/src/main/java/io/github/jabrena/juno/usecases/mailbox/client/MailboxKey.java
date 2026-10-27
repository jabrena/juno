package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.device.sd.SdCard;

import java.io.IOException;
import java.io.InputStream;

/**
 * The mailbox API key the client sends in a {@code Mailbox-API-Key} header on every mailbox request. It starts as
 * {@link #DEFAULT_KEY}, the board's own starting key, and once typed in Settings it is kept in {@code /api-key.txt} on
 * the SD card. The header line is encoded once per change, so a request only passes the ready bytes on.
 */
public final class MailboxKey {
    public static final String DEFAULT_KEY = "1234567890";
    public static final int MAX_KEY_CHARS = 16;
    private static final String HEADER_PREFIX = "Mailbox-API-Key: ";
    private static final String KEY_FILE = "api-key.txt";
    /** "Mailbox-API-Key: " (17 bytes), the longest key and the closing {@code \r\n}. */
    private static final int HEADER_CAPACITY = 17 + MAX_KEY_CHARS + 2;

    private final SdCard sdCard;
    private final char[] key = new char[MAX_KEY_CHARS];
    private final byte[] header = new byte[HEADER_CAPACITY];
    private int keyLength;
    private int headerLength;

    public MailboxKey(SdCard sdCard) {
        this.sdCard = sdCard;
        useDefault();
    }

    /** Reads the stored key from the SD card, which must already be begun; without one the default stays. */
    public void begin() {
        InputStream file = sdCard.open(KEY_FILE);
        if (file == null) {
            return;
        }
        int length = 0;
        try (InputStream openedFile = file) {
            while (openedFile.available() > 0) {
                int value = openedFile.read();
                if (value < 0 || length == MAX_KEY_CHARS && value > ' ') {
                    useDefault();
                    return;
                }
                if (value > ' ') {
                    key[length++] = (char) value;
                }
            }
        } catch (IOException exception) {
            useDefault();
            return;
        }
        if (length == 0) {
            useDefault();
            return;
        }
        keyLength = length;
        encodeHeader();
    }

    /**
     * Uses the first {@code length} characters of {@code value} from now on and stores them on the SD card; the new
     * key is used even when it could not be stored, and the result says whether it was.
     */
    public boolean change(char[] value, int length) {
        if (length <= 0 || length > MAX_KEY_CHARS) {
            return false;
        }
        StringBuilder text = new StringBuilder(MAX_KEY_CHARS);
        for (int index = 0; index < length; index++) {
            key[index] = value[index];
            text.append(value[index]);
        }
        keyLength = length;
        encodeHeader();
        sdCard.remove(KEY_FILE);
        return sdCard.appendText(KEY_FILE, text.toString());
    }

    /** Copies the key into {@code target}, which holds {@link #MAX_KEY_CHARS}, and returns its length. */
    public int copy(char[] target) {
        for (int index = 0; index < keyLength; index++) {
            target[index] = key[index];
        }
        return keyLength;
    }

    /** The {@code Mailbox-API-Key} header line, ending in {@code \r\n}; its length is {@link #headerLength()}. */
    public byte[] header() {
        return header;
    }

    public int headerLength() {
        return headerLength;
    }

    private void useDefault() {
        for (int index = 0; index < DEFAULT_KEY.length(); index++) {
            key[index] = DEFAULT_KEY.charAt(index);
        }
        keyLength = DEFAULT_KEY.length();
        encodeHeader();
    }

    private void encodeHeader() {
        int position = 0;
        for (int index = 0; index < HEADER_PREFIX.length(); index++) {
            header[position++] = (byte) HEADER_PREFIX.charAt(index);
        }
        for (int index = 0; index < keyLength; index++) {
            header[position++] = (byte) key[index];
        }
        header[position++] = '\r';
        header[position++] = '\n';
        headerLength = position;
    }
}
