package io.github.jabrena.juno.usecases.mailbox.server.security;

import io.github.jabrena.juno.hardware.device.tft.Mhs35TftDisplay;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.usecases.mailbox.server.cover.MailboxView;

/**
 * The Security screen, opened from the status screen: the current API key, the new key being typed, and a numeric
 * keypad to type it. The stored key changes only on OK; tapping the header leaves without changing it.
 */
public final class MailboxSecurityView {
    private static final int LABEL_X = 16;
    private static final int TEXT_SIZE = 2;
    private static final int CHAR_WIDTH = TEXT_SIZE * Mhs35TftDisplay.CHAR_WIDTH;
    private static final int ROW_HEIGHT = TEXT_SIZE * Mhs35TftDisplay.CHAR_HEIGHT;
    private static final int CURRENT_KEY_Y = 52;
    private static final int NEW_KEY_Y = 76;
    private static final int MESSAGE_Y = 98;

    /** The keypad: digits 1-9, then DEL, 0 and OK, in three columns of four rows. */
    private static final String[] KEY_LABELS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "DEL", "0", "OK"};
    private static final int KEYPAD_LEFT = 16;
    private static final int KEYPAD_TOP = 116;
    private static final int KEY_WIDTH = 64;
    private static final int KEY_HEIGHT = 42;
    private static final int KEY_GAP = 8;
    private static final int DELETE_KEY = 9;
    private static final int ZERO_KEY = 10;
    private static final int OK_KEY = 11;

    private final MailboxSecurity security;
    private final byte[] newKey = new byte[MailboxSecurity.MAX_KEY_BYTES];
    private final byte[] currentKey = new byte[MailboxSecurity.MAX_KEY_BYTES];
    private int newKeyLength;

    public MailboxSecurityView(MailboxSecurity security) {
        this.security = security;
    }

    /** Draws the screen with an empty new key. */
    public void show(Mhs35TftDisplay tft) {
        newKeyLength = 0;
        tft.fillScreen(Mhs35TftDisplay.WHITE);
        MailboxView.drawHeader(tft, "SECURITY");
        tft.setTextSize(1);
        tft.setTextColor(Mhs35TftDisplay.WHITE, MailboxView.BLUE);
        tft.setCursor(4, 4);
        tft.print("< BACK");

        tft.setTextSize(TEXT_SIZE);
        tft.setTextColor(MailboxView.TEXT, Mhs35TftDisplay.WHITE);
        tft.setCursor(LABEL_X, CURRENT_KEY_Y);
        tft.print("KEY ");
        tft.setTextColor(MailboxView.BLUE, Mhs35TftDisplay.WHITE);
        print(tft, currentKey, security.copyKey(currentKey));
        drawNewKey(tft);
        drawMessage(tft, "TYPE A NEW KEY, THEN OK", MailboxView.TEXT);
        for (int key = 0; key < KEY_LABELS.length; key++) {
            drawKey(tft, key);
        }
    }

    /** Handles a tap; true when the screen is done, after a tap on the header or a saved key. */
    public boolean touch(Mhs35TftDisplay tft, int x, int y) {
        if (y < MailboxView.HEADER_HEIGHT) {
            return true;
        }
        int key = keyAt(x, y);
        if (key == OK_KEY) {
            return save(tft);
        }
        if (key == DELETE_KEY) {
            if (newKeyLength > 0) {
                newKeyLength = newKeyLength - 1;
                drawNewKey(tft);
            }
        } else if (key >= 0 && newKeyLength < newKey.length) {
            newKey[newKeyLength] = (byte) (key == ZERO_KEY ? '0' : '1' + key);
            newKeyLength = newKeyLength + 1;
            drawNewKey(tft);
        }
        return false;
    }

    private boolean save(Mhs35TftDisplay tft) {
        if (newKeyLength < MailboxSecurity.MIN_KEY_BYTES) {
            drawMessage(tft, "USE AT LEAST 6 DIGITS", Mhs35TftDisplay.RED);
            return false;
        }
        if (!security.change(newKey, newKeyLength)) {
            drawMessage(tft, "COULD NOT SAVE TO THE SD CARD", Mhs35TftDisplay.RED);
            return false;
        }
        Serial.println("Mailbox API key changed");
        return true;
    }

    private void drawKey(Mhs35TftDisplay tft, int key) {
        int x = KEYPAD_LEFT + key % 3 * (KEY_WIDTH + KEY_GAP);
        int y = KEYPAD_TOP + key / 3 * (KEY_HEIGHT + KEY_GAP);
        int color = key == OK_KEY ? MailboxView.DARK_GREEN : key == DELETE_KEY ? Mhs35TftDisplay.RED : MailboxView.BLUE;
        tft.fillRect(x, y, KEY_WIDTH, KEY_HEIGHT, color);
        String label = KEY_LABELS[key];
        tft.setTextSize(TEXT_SIZE);
        tft.setTextColor(Mhs35TftDisplay.WHITE, color);
        tft.setCursor(x + (KEY_WIDTH - label.length() * CHAR_WIDTH) / 2, y + (KEY_HEIGHT - ROW_HEIGHT) / 2);
        tft.print(label);
    }

    private void drawNewKey(Mhs35TftDisplay tft) {
        tft.fillRect(0, NEW_KEY_Y, MailboxView.SCREEN_WIDTH, ROW_HEIGHT, Mhs35TftDisplay.WHITE);
        tft.setTextSize(TEXT_SIZE);
        tft.setCursor(LABEL_X, NEW_KEY_Y);
        tft.setTextColor(MailboxView.TEXT, Mhs35TftDisplay.WHITE);
        tft.print("NEW ");
        tft.setTextColor(MailboxView.BLUE, Mhs35TftDisplay.WHITE);
        print(tft, newKey, newKeyLength);
        tft.print("_");
    }

    private void drawMessage(Mhs35TftDisplay tft, String message, int color) {
        tft.fillRect(0, MESSAGE_Y, MailboxView.SCREEN_WIDTH, Mhs35TftDisplay.CHAR_HEIGHT,
                Mhs35TftDisplay.WHITE);
        tft.setTextSize(1);
        tft.setCursor(LABEL_X, MESSAGE_Y);
        tft.setTextColor(color, Mhs35TftDisplay.WHITE);
        tft.print(message);
    }

    /** The keypad key under the tap, or -1 between keys. */
    private int keyAt(int x, int y) {
        int column = (x - KEYPAD_LEFT) / (KEY_WIDTH + KEY_GAP);
        int row = (y - KEYPAD_TOP) / (KEY_HEIGHT + KEY_GAP);
        if (x < KEYPAD_LEFT || y < KEYPAD_TOP || column > 2 || row > 3
                || (x - KEYPAD_LEFT) % (KEY_WIDTH + KEY_GAP) >= KEY_WIDTH
                || (y - KEYPAD_TOP) % (KEY_HEIGHT + KEY_GAP) >= KEY_HEIGHT) {
            return -1;
        }
        return row * 3 + column;
    }

    /** The first {@code length} bytes of {@code text}, one character at a time. */
    private static void print(Mhs35TftDisplay tft, byte[] text, int length) {
        for (int index = 0; index < length; index++) {
            tft.print((char) text[index]);
        }
    }
}
