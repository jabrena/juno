package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/**
 * The mailbox TFT view, in portrait: a flat blue mailbox with a letter dropping into its slot, above the message
 * count and IP address, and a settings gear that opens a status screen with the SD-card and Wi-Fi state. Later
 * changes redraw only their own row on the cover, or the whole status screen while it is shown.
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

    private boolean storageReady;
    private int messageCount;
    private String internetState = "CONNECTING";
    private int internetColor = TftTouchShield.ORANGE;
    // The IP is kept as text bytes, not as the String it came in: a runtime String's slot is reused later.
    private final byte[] ip = new byte[IP_CAPACITY];
    private int ipLength;
    private boolean ipMissing;
    private boolean statusShown;

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
        if (statusShown) {
            drawStatusScreen();
        } else {
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

    /** A tap on the cover's gear opens the status screen; any tap on the status screen goes back to the cover. */
    public void touch(int x, int y) {
        if (statusShown) {
            statusShown = false;
            drawCoverScreen();
        } else if (x >= GEAR_TOUCH_LEFT && y >= GEAR_TOUCH_TOP) {
            statusShown = true;
            drawStatusScreen();
        }
    }

    private void drawInternet(String state, int color) {
        internetState = state;
        internetColor = color;
        if (statusShown) {
            drawStatusScreen();
        }
    }

    private void redrawIp() {
        if (statusShown) {
            drawStatusScreen();
        } else {
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
        drawGear();
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

    /** SD card, Wi-Fi, IP and message count, one labelled row each, above a "tap to go back" hint. */
    private void drawStatusScreen() {
        TftTouchShield.fillScreen(TftTouchShield.WHITE);
        TftTouchShield.fillRect(0, 0, SCREEN_WIDTH, HEADER_HEIGHT, BLUE);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, BLUE);
        TftTouchShield.setCursor((SCREEN_WIDTH - 6 * 3 * TftTouchShield.CHAR_WIDTH) / 2, 10);
        TftTouchShield.print("STATUS");

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

        startRow(BACK_Y, "TAP TO GO BACK".length());
        TftTouchShield.setTextColor(MOTION, TftTouchShield.WHITE);
        TftTouchShield.print("TAP TO GO BACK");
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
