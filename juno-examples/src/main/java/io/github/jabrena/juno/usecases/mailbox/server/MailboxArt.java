package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/** The cover's picture: a flat blue mailbox on two pale circles, with a letter dropping into its slot. */
public final class MailboxArt {
    private static final int LIGHT_BLUE = 0x4CBE;
    private static final int DARK_BLUE = 0x1AD7;
    private static final int PALE_BLUE = 0xEF9F;
    private static final int SHADOW = 0xD71E;
    private static final int ENVELOPE = 0xFE48;
    private static final int ENVELOPE_FLAP = 0xED46;

    /** The mailbox drawing's origin: the body's left edge and the top of its dome. */
    private static final int MAILBOX_X = 55;
    private static final int MAILBOX_Y = 24;
    private static final int BODY_WIDTH = 130;
    private static final int DOME_RADIUS = BODY_WIDTH / 2;
    private static final int SLOT_Y = 62;
    private static final int SLOT_HEIGHT = 9;
    private static final int BODY_BOTTOM = 175;
    private static final int LEG_HEIGHT = 18;

    void draw() {
        TftTouchShield.fillCircle(MAILBOX_X + 40, MAILBOX_Y + 55, 62, PALE_BLUE);
        TftTouchShield.fillCircle(MAILBOX_X + 120, MAILBOX_Y + 145, 40, PALE_BLUE);
        drawMailbox();
    }

    private void drawMailbox() {
        rect(-10, BODY_BOTTOM + LEG_HEIGHT, BODY_WIDTH + 20, 3, SHADOW);
        circle(DOME_RADIUS, DOME_RADIUS, DOME_RADIUS, MailboxView.BLUE);
        circle(DOME_RADIUS, DOME_RADIUS, 47, DARK_BLUE);
        circle(DOME_RADIUS, DOME_RADIUS, 43, MailboxView.BLUE);
        rect(32, SLOT_Y, BODY_WIDTH - 64, SLOT_HEIGHT, DARK_BLUE);

        drawEnvelope();

        // The body below the slot is drawn after the envelope, so the letter disappears into the slot.
        int bodyTop = SLOT_Y + SLOT_HEIGHT;
        rect(0, bodyTop, BODY_WIDTH, BODY_BOTTOM - bodyTop, MailboxView.BLUE);
        rect(0, DOME_RADIUS, 10, BODY_BOTTOM + LEG_HEIGHT - DOME_RADIUS, LIGHT_BLUE);
        rect(10, BODY_BOTTOM, 16, LEG_HEIGHT, MailboxView.BLUE);
        rect(BODY_WIDTH - 26, BODY_BOTTOM, 26, LEG_HEIGHT, DARK_BLUE);

        TftTouchShield.drawRect(MAILBOX_X + 10, MAILBOX_Y + 98, 110, 40, TftTouchShield.WHITE);
        TftTouchShield.drawRect(MAILBOX_X + 11, MAILBOX_Y + 99, 108, 38, TftTouchShield.WHITE);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, MailboxView.BLUE);
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
            TftTouchShield.drawPixel(MAILBOX_X + x + i, MAILBOX_Y + y - i, MailboxView.MOTION);
            TftTouchShield.drawPixel(MAILBOX_X + x + i + 1, MAILBOX_Y + y - i, MailboxView.MOTION);
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
