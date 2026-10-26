package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * The mailbox TFT view, in portrait: a flat blue mailbox with a letter dropping into its slot, above the message
 * count and IP address, and a settings gear that opens a status screen with the SD-card, Wi-Fi and API-key state.
 * From there the Security screen changes the API key on a numeric keypad. Later changes redraw only their own row on
 * the cover, or the whole status screen while it is shown.
 */
public final class MailboxView {
    private static final int BLUE = 0x3C1E;
    private static final int LIGHT_BLUE = 0x4CBE;
    private static final int DARK_BLUE = 0x1AD7;
    private static final int PALE_BLUE = 0xEF9F;
    private static final int SHADOW = 0xD71E;
    private static final int ENVELOPE = 0xFE48;
    private static final int ENVELOPE_FLAP = 0xED46;
    private static final int MOTION = 0x5ACB;
    private static final int TEXT = 0x19D1;
    private static final int DARK_GREEN = 0x0400;

    /** The mailbox drawing's origin: the body's left edge and the top of its dome. */
    private static final int MAILBOX_X = 55;
    private static final int MAILBOX_Y = 24;
    private static final int BODY_WIDTH = 130;
    private static final int DOME_RADIUS = BODY_WIDTH / 2;
    private static final int SLOT_Y = 62;
    private static final int SLOT_HEIGHT = 9;
    private static final int BODY_BOTTOM = 175;
    private static final int LEG_HEIGHT = 18;

    /** The cover's two status rows (message count, then IP address) share text size 2 and are each centered. */
    private static final int SCREEN_WIDTH = 240;
    private static final int STATUS_TEXT_SIZE = 2;
    private static final int STATUS_CHAR_WIDTH = STATUS_TEXT_SIZE * TftTouchShield.CHAR_WIDTH;
    private static final int STATUS_ROW_HEIGHT = STATUS_TEXT_SIZE * TftTouchShield.CHAR_HEIGHT;
    private static final int COUNT_Y = 232;
    private static final int IP_Y = 256;
    private static final String MESSAGES_LABEL = "MESSAGES: ";
    private static final String IP_HINT = "SET q.ip IN application.properties";
    private static final int IP_CAPACITY = 16;

    /** The settings gear in the bottom-right corner; a tap near it opens the status screen. */
    private static final int GEAR_X = 214;
    private static final int GEAR_Y = 296;
    private static final int GEAR_RADIUS = 11;
    private static final int GEAR_TOOTH_RADIUS = 4;
    private static final int[] GEAR_TOOTH_X = {0, 9, 13, 9, 0, -9, -13, -9};
    private static final int[] GEAR_TOOTH_Y = {-13, -9, 0, 9, 13, 9, 0, -9};
    private static final int GEAR_TOUCH_LEFT = 160;
    private static final int GEAR_TOUCH_TOP = 248;

    private static final int HEADER_HEIGHT = 44;
    private static final int LABEL_X = 16;
    private static final int VALUE_X = 88;
    private static final int FIRST_ROW_Y = 72;
    private static final int ROW_GAP = 34;
    private static final int BACK_Y = 292;
    private static final String DEFAULT_KEY_HINT = "DEFAULT API KEY: TAP THE GEAR";
    private static final int KEY_HINT_Y = 276;

    private static final int COVER = 0;
    private static final int STATUS = 1;
    private static final int SECURITY = 2;

    /** The status screen's two buttons share the bottom row: BACK on the left, SECURITY on the right. */
    private static final int BUTTON_TOP = 280;
    private static final int SECURITY_BUTTON_LEFT = 120;

    /** The Security keypad: digits 1-9, then DEL, 0 and OK, in three columns of four rows. */
    private static final String[] KEY_LABELS = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "DEL", "0", "OK"};
    private static final int KEYPAD_LEFT = 16;
    private static final int KEYPAD_TOP = 116;
    private static final int KEY_WIDTH = 64;
    private static final int KEY_HEIGHT = 42;
    private static final int KEY_GAP = 8;
    private static final int DELETE_KEY = 9;
    private static final int ZERO_KEY = 10;
    private static final int OK_KEY = 11;
    private static final int KEY_FIELD_Y = 76;
    private static final int KEY_MESSAGE_Y = 98;

    private boolean storageReady;
    private int messageCount;
    private String internetState = "CONNECTING";
    private int internetColor = TftTouchShield.ORANGE;
    // The IP is kept as text bytes, not as the String it came in: a runtime String's slot is reused later.
    private final byte[] ip = new byte[IP_CAPACITY];
    private int ipLength;
    private boolean ipMissing;
    private int screen = COVER;
    private final MailboxSecurity security;
    // The key being typed on the Security screen; the stored key changes only on OK.
    private final byte[] newKey = new byte[MailboxSecurity.MAX_KEY_BYTES];
    private final byte[] currentKey = new byte[MailboxSecurity.MAX_KEY_BYTES];
    private int newKeyLength;

    public MailboxView(MailboxSecurity security) {
        this.security = security;
    }

    public void drawCover(boolean storageReady, int messageCount) {
        this.storageReady = storageReady;
        this.messageCount = messageCount;
        ipLength = copy("CONNECTING");
        drawCoverScreen();
    }

    public void drawInternetConnecting() {
        drawInternet("CONNECTING", TftTouchShield.ORANGE);
    }

    /** The board joined the network and was given an address. */
    public void drawInternetOnline() {
        drawInternet("ONLINE", DARK_GREEN);
    }

    public void drawInternetOffline() {
        drawInternet("OFFLINE", TftTouchShield.RED);
    }

    public void drawMessageCount(int messageCount) {
        this.messageCount = messageCount;
        if (screen == STATUS) {
            drawStatusScreen();
        } else if (screen == COVER) {
            drawCountRow();
        }
    }

    public void drawIp(int[] octets) {
        ipMissing = false;
        ipLength = 0;
        for (int index = 0; index < octets.length; index++) {
            if (index > 0) {
                appendIp('.');
            }
            appendIpNumber(octets[index]);
        }
        redrawIp();
    }

    /** The UNO Q's address from {@code q.ip}, or {@code null} when it is not configured. */
    public void drawIp(String address) {
        ipMissing = address == null || address.length() > IP_CAPACITY;
        ipLength = ipMissing ? 0 : copy(address);
        redrawIp();
    }

    /**
     * A tap on the cover's gear opens the status screen. There, SECURITY opens the Security screen and any other tap
     * goes back to the cover; on the Security screen the keypad edits the key, and the header goes back to the status
     * screen without changing it.
     */
    public void touch(int x, int y) {
        if (screen == COVER) {
            if (x >= GEAR_TOUCH_LEFT && y >= GEAR_TOUCH_TOP) {
                screen = STATUS;
                drawStatusScreen();
            }
        } else if (screen == STATUS) {
            if (x >= SECURITY_BUTTON_LEFT && y >= BUTTON_TOP) {
                screen = SECURITY;
                newKeyLength = 0;
                drawSecurityScreen();
            } else {
                screen = COVER;
                drawCoverScreen();
            }
        } else if (y < HEADER_HEIGHT) {
            screen = STATUS;
            drawStatusScreen();
        } else {
            pressKey(keyAt(x, y));
        }
    }

    private void drawInternet(String state, int color) {
        internetState = state;
        internetColor = color;
        if (screen == STATUS) {
            drawStatusScreen();
        }
    }

    private void redrawIp() {
        if (screen == STATUS) {
            drawStatusScreen();
        } else if (screen == COVER) {
            drawIpRow();
        }
    }

    private void drawCoverScreen() {
        TftTouchShield.fillScreen(TftTouchShield.WHITE);
        TftTouchShield.fillCircle(MAILBOX_X + 40, MAILBOX_Y + 55, 62, PALE_BLUE);
        TftTouchShield.fillCircle(MAILBOX_X + 120, MAILBOX_Y + 145, 40, PALE_BLUE);
        drawMailbox();
        drawCountRow();
        drawIpRow();
        drawKeyHint();
        drawGear();
    }

    /** While the API key is still the default, the cover says so, so changing it is the obvious first step. */
    private void drawKeyHint() {
        if (!security.isDefault()) {
            return;
        }
        TftTouchShield.setTextSize(1);
        TftTouchShield.setCursor(8, KEY_HINT_Y);
        TftTouchShield.setTextColor(TftTouchShield.RED, TftTouchShield.WHITE);
        TftTouchShield.print(DEFAULT_KEY_HINT);
    }

    private void drawCountRow() {
        startRow(COUNT_Y, MESSAGES_LABEL.length() + digits(messageCount));
        TftTouchShield.setTextColor(TEXT, TftTouchShield.WHITE);
        TftTouchShield.print(MESSAGES_LABEL);
        TftTouchShield.setTextColor(BLUE, TftTouchShield.WHITE);
        TftTouchShield.print(messageCount);
    }

    /** The hint for a missing {@code q.ip} is too long for the row, so it falls back to the small font. */
    private void drawIpRow() {
        if (ipMissing) {
            TftTouchShield.fillRect(0, IP_Y, SCREEN_WIDTH, STATUS_ROW_HEIGHT, TftTouchShield.WHITE);
            TftTouchShield.setTextSize(1);
            TftTouchShield.setCursor((SCREEN_WIDTH - IP_HINT.length() * TftTouchShield.CHAR_WIDTH) / 2,
                    IP_Y + TftTouchShield.CHAR_HEIGHT / 2);
            TftTouchShield.setTextColor(TEXT, TftTouchShield.WHITE);
            TftTouchShield.print(IP_HINT);
            return;
        }
        startRow(IP_Y, ipLength);
        TftTouchShield.setTextColor(TEXT, TftTouchShield.WHITE);
        TftTouchShield.print(ip, ipLength);
    }

    private void drawGear() {
        for (int tooth = 0; tooth < GEAR_TOOTH_X.length; tooth++) {
            TftTouchShield.fillCircle(GEAR_X + GEAR_TOOTH_X[tooth], GEAR_Y + GEAR_TOOTH_Y[tooth], GEAR_TOOTH_RADIUS,
                    TEXT);
        }
        TftTouchShield.fillCircle(GEAR_X, GEAR_Y, GEAR_RADIUS, TEXT);
        TftTouchShield.fillCircle(GEAR_X, GEAR_Y, GEAR_TOOTH_RADIUS, TftTouchShield.WHITE);
    }

    /** SD card, Wi-Fi, IP, message count and API key, one labelled row each, above the BACK and SECURITY buttons. */
    private void drawStatusScreen() {
        TftTouchShield.fillScreen(TftTouchShield.WHITE);
        drawHeader("STATUS");

        statusLabel("SD", 0);
        TftTouchShield.setTextColor(storageReady ? DARK_GREEN : TftTouchShield.RED, TftTouchShield.WHITE);
        TftTouchShield.print(storageReady ? "READY" : "UNAVAILABLE");
        statusLabel("WIFI", 1);
        TftTouchShield.setTextColor(internetColor, TftTouchShield.WHITE);
        TftTouchShield.print(internetState);
        statusLabel("IP", 2);
        TftTouchShield.setTextColor(BLUE, TftTouchShield.WHITE);
        if (ipMissing) {
            TftTouchShield.print("SET q.ip");
        } else {
            TftTouchShield.print(ip, ipLength);
        }
        statusLabel("MAIL", 3);
        TftTouchShield.setTextColor(BLUE, TftTouchShield.WHITE);
        TftTouchShield.print(messageCount);
        statusLabel("KEY", 4);
        TftTouchShield.setTextColor(security.isDefault() ? TftTouchShield.RED : DARK_GREEN, TftTouchShield.WHITE);
        TftTouchShield.print(security.isDefault() ? "DEFAULT" : "SET");

        TftTouchShield.setTextSize(STATUS_TEXT_SIZE);
        TftTouchShield.setTextColor(MOTION, TftTouchShield.WHITE);
        TftTouchShield.setCursor(LABEL_X, BACK_Y);
        TftTouchShield.print("BACK");
        TftTouchShield.fillRect(SECURITY_BUTTON_LEFT, BUTTON_TOP, SCREEN_WIDTH - SECURITY_BUTTON_LEFT, 40, BLUE);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, BLUE);
        TftTouchShield.setCursor(SECURITY_BUTTON_LEFT + 12, BACK_Y);
        TftTouchShield.print("SECURITY");
    }

    private void drawHeader(String title) {
        TftTouchShield.fillRect(0, 0, SCREEN_WIDTH, HEADER_HEIGHT, BLUE);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, BLUE);
        TftTouchShield.setCursor((SCREEN_WIDTH - title.length() * 3 * TftTouchShield.CHAR_WIDTH) / 2, 10);
        TftTouchShield.print(title);
    }

    /** The current key, the new key being typed, and the keypad; tapping the header goes back. */
    private void drawSecurityScreen() {
        TftTouchShield.fillScreen(TftTouchShield.WHITE);
        drawHeader("SECURITY");
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, BLUE);
        TftTouchShield.setCursor(4, 4);
        TftTouchShield.print("< BACK");

        TftTouchShield.setTextSize(STATUS_TEXT_SIZE);
        TftTouchShield.setTextColor(TEXT, TftTouchShield.WHITE);
        TftTouchShield.setCursor(LABEL_X, 52);
        TftTouchShield.print("KEY ");
        TftTouchShield.setTextColor(BLUE, TftTouchShield.WHITE);
        TftTouchShield.print(currentKey, security.copyKey(currentKey));
        drawNewKey();
        drawKeyMessage("TYPE A NEW KEY, THEN OK", TEXT);
        for (int key = 0; key < KEY_LABELS.length; key++) {
            int x = KEYPAD_LEFT + key % 3 * (KEY_WIDTH + KEY_GAP);
            int y = KEYPAD_TOP + key / 3 * (KEY_HEIGHT + KEY_GAP);
            int color = key == OK_KEY ? DARK_GREEN : key == DELETE_KEY ? TftTouchShield.RED : BLUE;
            TftTouchShield.fillRect(x, y, KEY_WIDTH, KEY_HEIGHT, color);
            String label = KEY_LABELS[key];
            TftTouchShield.setTextSize(STATUS_TEXT_SIZE);
            TftTouchShield.setTextColor(TftTouchShield.WHITE, color);
            TftTouchShield.setCursor(x + (KEY_WIDTH - label.length() * STATUS_CHAR_WIDTH) / 2,
                    y + (KEY_HEIGHT - STATUS_ROW_HEIGHT) / 2);
            TftTouchShield.print(label);
        }
    }

    private void drawNewKey() {
        TftTouchShield.fillRect(0, KEY_FIELD_Y, SCREEN_WIDTH, STATUS_ROW_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(STATUS_TEXT_SIZE);
        TftTouchShield.setCursor(LABEL_X, KEY_FIELD_Y);
        TftTouchShield.setTextColor(TEXT, TftTouchShield.WHITE);
        TftTouchShield.print("NEW ");
        TftTouchShield.setTextColor(BLUE, TftTouchShield.WHITE);
        TftTouchShield.print(newKey, newKeyLength);
        TftTouchShield.print("_");
    }

    private void drawKeyMessage(String message, int color) {
        TftTouchShield.fillRect(0, KEY_MESSAGE_Y, SCREEN_WIDTH, TftTouchShield.CHAR_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setCursor(LABEL_X, KEY_MESSAGE_Y);
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

    private void pressKey(int key) {
        if (key == DELETE_KEY) {
            if (newKeyLength > 0) {
                newKeyLength = newKeyLength - 1;
                drawNewKey();
            }
        } else if (key == OK_KEY) {
            saveKey();
        } else if (key >= 0 && newKeyLength < newKey.length) {
            newKey[newKeyLength] = (byte) (key == ZERO_KEY ? '0' : '1' + key);
            newKeyLength = newKeyLength + 1;
            drawNewKey();
        }
    }

    private void saveKey() {
        if (newKeyLength < MailboxSecurity.MIN_KEY_BYTES) {
            drawKeyMessage("USE AT LEAST 6 DIGITS", TftTouchShield.RED);
        } else if (security.change(newKey, newKeyLength)) {
            Serial.println("Mailbox API key changed");
            screen = STATUS;
            drawStatusScreen();
        } else {
            drawKeyMessage("COULD NOT SAVE TO THE SD CARD", TftTouchShield.RED);
        }
    }

    private void statusLabel(String label, int row) {
        int y = FIRST_ROW_Y + row * ROW_GAP;
        TftTouchShield.setTextSize(STATUS_TEXT_SIZE);
        TftTouchShield.setTextColor(TEXT, TftTouchShield.WHITE);
        TftTouchShield.setCursor(LABEL_X, y);
        TftTouchShield.print(label);
        TftTouchShield.setCursor(VALUE_X, y);
    }

    /** Clears a status row and puts the cursor where {@code characters} at the status size end up centered. */
    private void startRow(int y, int characters) {
        TftTouchShield.fillRect(0, y, SCREEN_WIDTH, STATUS_ROW_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(STATUS_TEXT_SIZE);
        TftTouchShield.setCursor(Math.max(0, (SCREEN_WIDTH - characters * STATUS_CHAR_WIDTH) / 2), y);
    }

    private int copy(String text) {
        int length = Math.min(text.length(), IP_CAPACITY);
        for (int index = 0; index < length; index++) {
            ip[index] = (byte) text.charAt(index);
        }
        return length;
    }

    private void appendIpNumber(int value) {
        if (value >= 100) {
            appendIp((char) ('0' + value / 100));
        }
        if (value >= 10) {
            appendIp((char) ('0' + value / 10 % 10));
        }
        appendIp((char) ('0' + value % 10));
    }

    private void appendIp(char character) {
        if (ipLength < IP_CAPACITY) {
            ip[ipLength] = (byte) character;
            ipLength = ipLength + 1;
        }
    }

    private int digits(int value) {
        int count = value < 0 ? 2 : 1;
        for (int rest = Math.abs(value); rest >= 10; rest /= 10) {
            count++;
        }
        return count;
    }

    private void drawMailbox() {
        rect(-10, BODY_BOTTOM + LEG_HEIGHT, BODY_WIDTH + 20, 3, SHADOW);
        circle(DOME_RADIUS, DOME_RADIUS, DOME_RADIUS, BLUE);
        circle(DOME_RADIUS, DOME_RADIUS, 47, DARK_BLUE);
        circle(DOME_RADIUS, DOME_RADIUS, 43, BLUE);
        rect(32, SLOT_Y, BODY_WIDTH - 64, SLOT_HEIGHT, DARK_BLUE);

        drawEnvelope();

        // The body below the slot is drawn after the envelope, so the letter disappears into the slot.
        int bodyTop = SLOT_Y + SLOT_HEIGHT;
        rect(0, bodyTop, BODY_WIDTH, BODY_BOTTOM - bodyTop, BLUE);
        rect(0, DOME_RADIUS, 10, BODY_BOTTOM + LEG_HEIGHT - DOME_RADIUS, LIGHT_BLUE);
        rect(10, BODY_BOTTOM, 16, LEG_HEIGHT, BLUE);
        rect(BODY_WIDTH - 26, BODY_BOTTOM, 26, LEG_HEIGHT, DARK_BLUE);

        TftTouchShield.drawRect(MAILBOX_X + 10, MAILBOX_Y + 98, 110, 40, TftTouchShield.WHITE);
        TftTouchShield.drawRect(MAILBOX_X + 11, MAILBOX_Y + 99, 108, 38, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, BLUE);
        TftTouchShield.setCursor(MAILBOX_X + 17, MAILBOX_Y + 110);
        TftTouchShield.print("MAIL BOX");
    }

    private void drawEnvelope() {
        // A 60x40 letter tilted 35 degrees, its lower corner already inside the slot.
        fillTriangle(34, 39, 83, 4, 106, 37, ENVELOPE);
        fillTriangle(34, 39, 106, 37, 59, 72, ENVELOPE);
        fillTriangle(34, 39, 83, 4, 70, 38, ENVELOPE_FLAP);
        drawMotionLine(110, 6);
        drawMotionLine(120, 14);
        drawMotionLine(130, 22);
    }

    private void rect(int x, int y, int w, int h, int color) {
        TftTouchShield.fillRect(MAILBOX_X + x, MAILBOX_Y + y, w, h, color);
    }

    private void circle(int cx, int cy, int r, int color) {
        TftTouchShield.fillCircle(MAILBOX_X + cx, MAILBOX_Y + cy, r, color);
    }

    private void drawMotionLine(int x, int y) {
        for (int i = 0; i < 10; i++) {
            TftTouchShield.drawPixel(MAILBOX_X + x + i, MAILBOX_Y + y - i, MOTION);
            TftTouchShield.drawPixel(MAILBOX_X + x + i + 1, MAILBOX_Y + y - i, MOTION);
        }
    }

    private void fillTriangle(int x0, int y0, int x1, int y1, int x2, int y2, int color) {
        boolean clockwise = edge(x0, y0, x1, y1, x2, y2) > 0;
        int minX = Math.min(x0, Math.min(x1, x2));
        int maxX = Math.max(x0, Math.max(x1, x2));
        int maxY = Math.max(y0, Math.max(y1, y2));
        for (int y = Math.min(y0, Math.min(y1, y2)); y <= maxY; y++) {
            int left = -1;
            int right = -1;
            for (int x = minX; x <= maxX; x++) {
                if (inside(edge(x0, y0, x1, y1, x, y), clockwise)
                        && inside(edge(x1, y1, x2, y2, x, y), clockwise)
                        && inside(edge(x2, y2, x0, y0, x, y), clockwise)) {
                    if (left < 0) {
                        left = x;
                    }
                    right = x;
                }
            }
            if (left >= 0) {
                TftTouchShield.drawHorizontalLine(MAILBOX_X + left, MAILBOX_Y + y, right - left + 1, color);
            }
        }
    }

    private boolean inside(int edge, boolean clockwise) {
        return clockwise ? edge >= 0 : edge <= 0;
    }

    private int edge(int ax, int ay, int bx, int by, int px, int py) {
        return (bx - ax) * (py - ay) - (by - ay) * (px - ax);
    }
}
