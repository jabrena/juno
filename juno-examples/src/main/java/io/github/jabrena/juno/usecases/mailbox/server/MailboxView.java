package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/**
 * The mailbox TFT view, in portrait: a flat blue mailbox with a letter dropping into its slot, above the message
 * count, IP address and SD-card state. The cover is drawn once; later changes redraw only their own status line.
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

    /** The mailbox drawing's origin: the body's left edge and the top of its dome. */
    private static final int MAILBOX_X = 55;
    private static final int MAILBOX_Y = 24;
    private static final int BODY_WIDTH = 130;
    private static final int DOME_RADIUS = BODY_WIDTH / 2;
    private static final int SLOT_Y = 62;
    private static final int SLOT_HEIGHT = 9;
    private static final int BODY_BOTTOM = 175;
    private static final int LEG_HEIGHT = 18;

    private static final int STATUS_X = 54;
    private static final int COUNT_X = STATUS_X + 10 * 12;
    private static final int COUNT_Y = 238;
    private static final int IP_Y = 266;
    private static final int SD_Y = 282;
    private static final int INTERNET_Y = 298;
    private static final int STATUS_WIDTH = 240 - STATUS_X;

    public void drawCover(boolean storageReady, int messageCount) {
        TftTouchShield.fillScreen(TftTouchShield.WHITE);
        TftTouchShield.fillCircle(MAILBOX_X + 40, MAILBOX_Y + 55, 62, PALE_BLUE);
        TftTouchShield.fillCircle(MAILBOX_X + 120, MAILBOX_Y + 145, 40, PALE_BLUE);
        drawMailbox();

        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TEXT, TftTouchShield.WHITE);
        TftTouchShield.setCursor(STATUS_X, COUNT_Y);
        TftTouchShield.print("MESSAGES:");
        drawMessageCount(messageCount);
        drawIpLabel();
        TftTouchShield.print("CONNECTING");
        TftTouchShield.setTextColor(storageReady ? TftTouchShield.GREEN : TftTouchShield.RED, TftTouchShield.WHITE);
        TftTouchShield.setCursor(STATUS_X, SD_Y);
        TftTouchShield.print(storageReady ? "SD READY" : "SD UNAVAILABLE");
        drawInternetConnecting();
    }

    public void drawInternetConnecting() {
        drawInternet("INTERNET: CONNECTING", TftTouchShield.ORANGE);
    }

    /** The board joined the network and was given an address. */
    public void drawInternetOnline() {
        drawInternet("INTERNET: ONLINE", TftTouchShield.GREEN);
    }

    public void drawInternetOffline() {
        drawInternet("INTERNET: OFFLINE", TftTouchShield.RED);
    }

    private void drawInternet(String status, int color) {
        TftTouchShield.fillRect(STATUS_X, INTERNET_Y, STATUS_WIDTH, TftTouchShield.CHAR_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(color, TftTouchShield.WHITE);
        TftTouchShield.setCursor(STATUS_X, INTERNET_Y);
        TftTouchShield.print(status);
    }

    public void drawMessageCount(int messageCount) {
        TftTouchShield.fillRect(COUNT_X, COUNT_Y, 240 - COUNT_X, 16, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(BLUE, TftTouchShield.WHITE);
        TftTouchShield.setCursor(COUNT_X, COUNT_Y);
        TftTouchShield.print(messageCount);
    }

    public void drawIp(int[] ip) {
        drawIpLabel();
        TftTouchShield.print(ip[0]);
        TftTouchShield.print(".");
        TftTouchShield.print(ip[1]);
        TftTouchShield.print(".");
        TftTouchShield.print(ip[2]);
        TftTouchShield.print(".");
        TftTouchShield.print(ip[3]);
    }

    public void drawIp(String ip) {
        drawIpLabel();
        TftTouchShield.print(ip);
    }

    private void drawIpLabel() {
        TftTouchShield.fillRect(STATUS_X, IP_Y, STATUS_WIDTH, TftTouchShield.CHAR_HEIGHT, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(1);
        TftTouchShield.setTextColor(TEXT, TftTouchShield.WHITE);
        TftTouchShield.setCursor(STATUS_X, IP_Y);
        TftTouchShield.print("IP: ");
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
