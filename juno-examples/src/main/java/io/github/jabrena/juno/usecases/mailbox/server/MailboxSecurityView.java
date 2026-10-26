package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * The Security screen, opened from the status screen: the current API key, the new key being typed, and a numeric
 * keypad to type it. The stored key changes only on OK; tapping the header leaves without changing it.
 */
public final class MailboxSecurityView {
    private static final int LABEL_X = 16;
    private static final int TEXT_SIZE = 2;
    private static final int CHAR_WIDTH = TEXT_SIZE * TftTouchShield.CHAR_WIDTH;
    private static final int ROW_HEIGHT = TEXT_SIZE * TftTouchShield.CHAR_HEIGHT;
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
    void show() {
        newKeyLength = 0;
        TftTouchShield.fillScreen(TftTouchShield.WHITE);
        MailboxView.drawHeader("SECURITY");
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, MailboxView.BLUE);
        TftTouchShield.setCursor(4, 4);
        TftTouchShield.print("< BACK");

        TftTouchShield.setTextSize(TEXT_SIZE);
        TftTouchShield.setTextColor(MailboxView.TEXT, TftTouchShield.WHITE);
        TftTouchShield.setCursor(LABEL_X, CURRENT_KEY_Y);
        TftTouchShield.print("KEY ");
        TftTouchShield.setTextColor(MailboxView.BLUE, TftTouchShield.WHITE);
        TftTouchShield.print(currentKey, security.copyKey(currentKey));
        drawNewKey();
        drawMessage("TYPE A NEW KEY, THEN OK", MailboxView.TEXT);
        for (int key = 0; key < KEY_LABELS.length; key++) {
            drawKey(key);
        }
    }

    /** Handles a tap; true when the screen is done, after a tap on the header or a saved key. */
    boolean touch(int x, int y) {
        if (y < MailboxView.HEADER_HEIGHT) {
            return true;
        }
        int key = keyAt(x, y);
        if (key == OK_KEY) {
            return save();
        }
        if (key == DELETE_KEY) {
            if (newKeyLength > 0) {
                newKeyLength = newKeyLength - 1;
                drawNewKey();
            }
        } else if (key >= 0 && newKeyLength < newKey.length) {
            newKey[newKeyLength] = (byte) (key == ZERO_KEY ? '0' : '1' + key);
            newKeyLength = newKeyLength + 1;
            drawNewKey();
        }
        return false;
    }

    private boolean save() {
        if (newKeyLength < MailboxSecurity.MIN_KEY_BYTES) {
            drawMessage("USE AT LEAST 6 DIGITS", TftTouchShield.RED);
            return false;
        }
        if (!security.change(newKey, newKeyLength)) {
            drawMessage("COULD NOT SAVE TO THE SD CARD", TftTouchShield.RED);
            return false;
        }
        Serial.println("Mailbox API key changed");
        return true;
    }

    private void drawKey(int key) {
        int x = KEYPAD_LEFT + key % 3 * (KEY_WIDTH + KEY_GAP);
        int y = KEYPAD_TOP + key / 3 * (KEY_HEIGHT + KEY_GAP);
        int color = key == OK_KEY ? MailboxView.DARK_GREEN : key == DELETE_KEY ? TftTouchShield.RED : MailboxView.BLUE;
        TftTouchShield.fillRect(x, y, KEY_WIDTH, KEY_HEIGHT, color);
        String label = KEY_LABELS[key];
        TftTouchShield.setTextSize(TEXT_SIZE);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
        TftTouchShield.setCursor(x + (KEY_WIDTH - label.length() * CHAR_WIDTH) / 2, y + (KEY_HEIGHT - ROW_HEIGHT) / 2);
        TftTouchShield.print(label);
    }

    private void drawNewKey() {
        TftTouchShield.fillRect(0, NEW_KEY_Y, MailboxView.SCREEN_WIDTH, ROW_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(TEXT_SIZE);
        TftTouchShield.setCursor(LABEL_X, NEW_KEY_Y);
        TftTouchShield.setTextColor(MailboxView.TEXT, TftTouchShield.WHITE);
        TftTouchShield.print("NEW ");
        TftTouchShield.setTextColor(MailboxView.BLUE, TftTouchShield.WHITE);
        TftTouchShield.print(newKey, newKeyLength);
        TftTouchShield.print("_");
    }

    private void drawMessage(String message, int color) {
        TftTouchShield.fillRect(0, MESSAGE_Y, MailboxView.SCREEN_WIDTH, TftTouchShield.CHAR_HEIGHT,
                TftTouchShield.WHITE);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setCursor(LABEL_X, MESSAGE_Y);
        TftTouchShield.setTextColor(color, TftTouchShield.WHITE);
        TftTouchShield.print(message);
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
}
