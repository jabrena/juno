package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.device.sd.SdCard;

import java.io.IOException;
import java.io.InputStream;

/** The persisted interval between automatic mailbox refreshes, in minutes. */
public final class MailboxPolling {
    public static final int DEFAULT_MINUTES = 5;
    public static final int MIN_MINUTES = 1;
    public static final int MAX_MINUTES = 60;
    private static final int MAX_DIGITS = 2;
    private static final String FILE = "polling-minutes.txt";

    private final SdCard sdCard;
    private int minutes = DEFAULT_MINUTES;

    public MailboxPolling(SdCard sdCard) {
        this.sdCard = sdCard;
    }

    /** Reads the saved interval; a missing or invalid file leaves the five-minute default. */
    public void begin() {
        try (InputStream file = sdCard.open(FILE)) {
            if (file == null) {
                return;
            }
            int value = 0;
            int digits = 0;
            while (file.available() > 0) {
                int character = file.read();
                if (character >= '0' && character <= '9') {
                    if (digits == MAX_DIGITS) {
                        return;
                    }
                    value = value * 10 + character - '0';
                    digits++;
                } else if (character > ' ') {
                    return;
                }
            }
            if (digits > 0 && valid(value)) {
                minutes = value;
            }
        } catch (IOException exception) {
            // Keep the default when the optional setting cannot be read.
        }
    }

    public int minutes() {
        return minutes;
    }

    /** Copies the current value into a two-character settings field. */
    public int copy(char[] target) {
        if (minutes >= 10) {
            target[0] = (char) ('0' + minutes / 10);
            target[1] = (char) ('0' + minutes % 10);
            return 2;
        }
        target[0] = (char) ('0' + minutes);
        return 1;
    }

    /** Applies and persists a typed interval; returns false when it is outside {@code 1..60}. */
    public boolean change(char[] value, int length) {
        if (length <= 0 || length > MAX_DIGITS) {
            return false;
        }
        int next = 0;
        for (int index = 0; index < length; index++) {
            char character = value[index];
            if (character < '0' || character > '9') {
                return false;
            }
            next = next * 10 + character - '0';
        }
        if (!valid(next)) {
            return false;
        }
        minutes = next;
        StringBuilder text = new StringBuilder(MAX_DIGITS);
        for (int index = 0; index < length; index++) {
            text.append(value[index]);
        }
        sdCard.remove(FILE);
        return sdCard.appendText(FILE, text.toString());
    }

    private boolean valid(int value) {
        return value >= MIN_MINUTES && value <= MAX_MINUTES;
    }
}
