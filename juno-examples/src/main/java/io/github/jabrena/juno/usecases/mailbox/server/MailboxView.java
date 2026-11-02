package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/**
 * The mailbox TFT view, in portrait: a flat blue mailbox with a letter dropping into its slot, above the message
 * count, IP address, and HTTPS state, and a settings gear that opens a status screen with the SD-card, Wi-Fi and
 * API-key state.
 * From there the Security screen changes the API key on a numeric keypad. Later changes redraw only their own row on
 * the cover, or the whole status screen while it is shown.
 */
public final class MailboxView {
    static final int BLUE = 0x3C1E;
    static final int MOTION = 0x5ACB;
    static final int TEXT = 0x19D1;
    static final int DARK_GREEN = 0x0400;

    /** The cover's three status rows (message count, IP address, then HTTPS) share text size 2 and are each centered. */
    static final int SCREEN_WIDTH = 240;
    private static final int STATUS_TEXT_SIZE = 2;
    private static final int STATUS_CHAR_WIDTH = STATUS_TEXT_SIZE * TftTouchShield.CHAR_WIDTH;
    private static final int STATUS_ROW_HEIGHT = STATUS_TEXT_SIZE * TftTouchShield.CHAR_HEIGHT;
    private static final int COUNT_Y = 232;
    private static final int IP_Y = 256;
    private static final int HTTPS_Y = 272;
    private static final String HTTPS_LABEL = "HTTPS: ";
    private static final String MESSAGES_LABEL = "MESSAGES: ";
    private static final String IP_HINT = "SET JUNO_Q_IP AT BUILD TIME";
    private static final int IP_CAPACITY = 16;

    /** The settings gear in the bottom-right corner; a tap near it opens the status screen. */
    private static final int GEAR_X = 214;
    private static final int GEAR_Y = 304;
    private static final int GEAR_RADIUS = 11;
    private static final int GEAR_TOOTH_RADIUS = 4;
    private static final int[] GEAR_TOOTH_X = {0, 9, 13, 9, 0, -9, -13, -9};
    private static final int[] GEAR_TOOTH_Y = {-13, -9, 0, 9, 13, 9, 0, -9};
    private static final int GEAR_TOUCH_LEFT = 160;
    private static final int GEAR_TOUCH_TOP = 248;

    static final int HEADER_HEIGHT = 44;
    private static final int LABEL_X = 16;
    private static final int VALUE_X = 88;
    private static final int FIRST_ROW_Y = 72;
    private static final int ROW_GAP = 34;
    private static final int BACK_Y = 292;
    private static final String DEFAULT_KEY_HINT = "DEFAULT API KEY: TAP THE GEAR";
    private static final int KEY_HINT_Y = 292;

    private static final int COVER = 0;
    private static final int STATUS = 1;
    private static final int SECURITY = 2;

    /** The status screen's two buttons share the bottom row: BACK on the left, SECURITY on the right. */
    private static final int BUTTON_TOP = 280;
    private static final int SECURITY_BUTTON_LEFT = 120;

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
    private final MailboxSecurityView securityView;
    private final MailboxArt art = new MailboxArt();

    public MailboxView(MailboxSecurity security) {
        this.security = security;
        this.securityView = new MailboxSecurityView(security);
    }

    public void drawCover(TftTouchShield tft, boolean storageReady, int messageCount) {
        this.storageReady = storageReady;
        this.messageCount = messageCount;
        drawCoverScreen(tft);
    }

    /** Publishes SD readiness without redrawing the mailbox art while startup work is still active. */
    public void updateStorageReady(TftTouchShield tft, boolean storageReady) {
        this.storageReady = storageReady;
        if (screen == STATUS) {
            drawStorageStatus(tft);
        }
    }

    public void drawInternetConnecting(TftTouchShield tft) {
        drawInternet(tft, "CONNECTING", TftTouchShield.ORANGE);
    }

    /** The board joined the network and was given an address. */
    public void drawInternetOnline(TftTouchShield tft) {
        drawInternet(tft, "ONLINE", DARK_GREEN);
    }

    public void drawInternetOffline(TftTouchShield tft) {
        drawInternet(tft, "OFFLINE", TftTouchShield.RED);
    }

    public void drawMessageCount(TftTouchShield tft, int messageCount) {
        this.messageCount = messageCount;
        if (screen == STATUS) {
            drawStatusScreen(tft);
        } else if (screen == COVER) {
            drawCountRow(tft);
        }
    }

    public void drawIp(TftTouchShield tft, int[] octets) {
        ipMissing = false;
        ipLength = 0;
        for (int index = 0; index < octets.length; index++) {
            if (index > 0) {
                appendIp('.');
            }
            appendIpNumber(octets[index]);
        }
        redrawIp(tft);
    }

    /** The UNO Q's address from {@code JUNO_Q_IP}, or {@code null} when it is not configured. */
    public void drawIp(TftTouchShield tft, String address) {
        ipMissing = address == null || address.length() > IP_CAPACITY;
        ipLength = ipMissing ? 0 : copy(address);
        redrawIp(tft);
    }

    /**
     * A tap on the cover's gear opens the status screen. There, SECURITY opens the Security screen and any other tap
     * goes back to the cover; on the Security screen the keypad edits the key, and the header goes back to the status
     * screen without changing it.
     */
    public void touch(TftTouchShield tft, int x, int y) {
        if (screen == COVER) {
            if (x >= GEAR_TOUCH_LEFT && y >= GEAR_TOUCH_TOP) {
                screen = STATUS;
                drawStatusScreen(tft);
            }
        } else if (screen == STATUS) {
            if (x >= SECURITY_BUTTON_LEFT && y >= BUTTON_TOP && storageReady) {
                screen = SECURITY;
                securityView.show(tft);
            } else {
                screen = COVER;
                drawCoverScreen(tft);
            }
        } else if (securityView.touch(tft, x, y)) {
            screen = STATUS;
            drawStatusScreen(tft);
        }
    }

    private void drawInternet(TftTouchShield tft, String state, int color) {
        internetState = state;
        internetColor = color;
        if (screen == STATUS) {
            drawStatusScreen(tft);
        } else if (screen == COVER) {
            drawInternetRow(tft);
        }
    }

    /** The cover's third row shows the HTTPS listener state without hiding the configured IP. */
    private void drawInternetRow(TftTouchShield tft) {
        startRow(tft, HTTPS_Y, HTTPS_LABEL.length() + internetState.length());
        tft.setTextColor(TEXT, TftTouchShield.WHITE);
        tft.print(HTTPS_LABEL);
        tft.setTextColor(internetColor, TftTouchShield.WHITE);
        tft.print(internetState);
    }

    private void redrawIp(TftTouchShield tft) {
        if (screen == STATUS) {
            drawStatusScreen(tft);
        } else if (screen == COVER) {
            drawIpRow(tft);
        }
    }

    private void drawCoverScreen(TftTouchShield tft) {
        tft.fillScreen(TftTouchShield.WHITE);
        art.draw(tft);
        drawCountRow(tft);
        drawIpRow(tft);
        drawInternetRow(tft);
        drawKeyHint(tft);
        drawGear(tft);
    }

    /** While the API key is still the default, the cover says so, so changing it is the obvious first step. */
    private void drawKeyHint(TftTouchShield tft) {
        if (!security.isDefault()) {
            return;
        }
        tft.setTextSize(1);
        tft.setCursor(8, KEY_HINT_Y);
        tft.setTextColor(TftTouchShield.RED, TftTouchShield.WHITE);
        tft.print(DEFAULT_KEY_HINT);
    }

    private void drawCountRow(TftTouchShield tft) {
        startRow(tft, COUNT_Y, MESSAGES_LABEL.length() + digits(messageCount));
        tft.setTextColor(TEXT, TftTouchShield.WHITE);
        tft.print(MESSAGES_LABEL);
        tft.setTextColor(BLUE, TftTouchShield.WHITE);
        tft.print(messageCount);
    }

    /** The hint for a missing {@code JUNO_Q_IP} is too long for the row, so it falls back to the small font. */
    private void drawIpRow(TftTouchShield tft) {
        if (ipMissing) {
            tft.fillRect(0, IP_Y, SCREEN_WIDTH, STATUS_ROW_HEIGHT, TftTouchShield.WHITE);
            tft.setTextSize(1);
            tft.setCursor((SCREEN_WIDTH - IP_HINT.length() * TftTouchShield.CHAR_WIDTH) / 2,
                    IP_Y + TftTouchShield.CHAR_HEIGHT / 2);
            tft.setTextColor(TEXT, TftTouchShield.WHITE);
            tft.print(IP_HINT);
            return;
        }
        startRow(tft, IP_Y, ipLength);
        tft.setTextColor(TEXT, TftTouchShield.WHITE);
        tft.print(ip, ipLength);
    }

    private void drawGear(TftTouchShield tft) {
        for (int tooth = 0; tooth < GEAR_TOOTH_X.length; tooth++) {
            tft.fillCircle(GEAR_X + GEAR_TOOTH_X[tooth], GEAR_Y + GEAR_TOOTH_Y[tooth], GEAR_TOOTH_RADIUS,
                    TEXT);
        }
        tft.fillCircle(GEAR_X, GEAR_Y, GEAR_RADIUS, TEXT);
        tft.fillCircle(GEAR_X, GEAR_Y, GEAR_TOOTH_RADIUS, TftTouchShield.WHITE);
    }

    /** SD card, network, IP, message count and API key, one labelled row each, above the BACK and SECURITY buttons. */
    private void drawStatusScreen(TftTouchShield tft) {
        tft.fillScreen(TftTouchShield.WHITE);
        drawHeader(tft, "STATUS");

        drawStorageStatus(tft);
        statusLabel(tft, "NET", 1);
        tft.setTextColor(internetColor, TftTouchShield.WHITE);
        tft.print(internetState);
        statusLabel(tft, "IP", 2);
        tft.setTextColor(BLUE, TftTouchShield.WHITE);
        if (ipMissing) {
            tft.print("SET JUNO_Q_IP");
        } else {
            tft.print(ip, ipLength);
        }
        statusLabel(tft, "MAIL", 3);
        tft.setTextColor(BLUE, TftTouchShield.WHITE);
        tft.print(messageCount);
        statusLabel(tft, "KEY", 4);
        tft.setTextColor(security.isDefault() ? TftTouchShield.RED : DARK_GREEN, TftTouchShield.WHITE);
        tft.print(security.isDefault() ? "DEFAULT" : "SET");

        tft.setTextSize(STATUS_TEXT_SIZE);
        tft.setTextColor(MOTION, TftTouchShield.WHITE);
        tft.setCursor(LABEL_X, BACK_Y);
        tft.print("BACK");
        tft.fillRect(SECURITY_BUTTON_LEFT, BUTTON_TOP, SCREEN_WIDTH - SECURITY_BUTTON_LEFT, 40, BLUE);
        tft.setTextColor(TftTouchShield.WHITE, BLUE);
        tft.setCursor(SECURITY_BUTTON_LEFT + 12, BACK_Y);
        tft.print("SECURITY");
    }

    /** The blue title bar the status and Security screens share. */
    static void drawHeader(TftTouchShield tft, String title) {
        tft.fillRect(0, 0, SCREEN_WIDTH, HEADER_HEIGHT, BLUE);
        tft.setTextSize(3);
        tft.setTextColor(TftTouchShield.WHITE, BLUE);
        tft.setCursor((SCREEN_WIDTH - title.length() * 3 * TftTouchShield.CHAR_WIDTH) / 2, 10);
        tft.print(title);
    }

    private void statusLabel(TftTouchShield tft, String label, int row) {
        int y = FIRST_ROW_Y + row * ROW_GAP;
        tft.setTextSize(STATUS_TEXT_SIZE);
        tft.setTextColor(TEXT, TftTouchShield.WHITE);
        tft.setCursor(LABEL_X, y);
        tft.print(label);
        tft.setCursor(VALUE_X, y);
    }

    private void drawStorageStatus(TftTouchShield tft) {
        tft.fillRect(VALUE_X, FIRST_ROW_Y, SCREEN_WIDTH - VALUE_X, STATUS_ROW_HEIGHT,
                TftTouchShield.WHITE);
        statusLabel(tft, "SD", 0);
        tft.setTextColor(storageReady ? DARK_GREEN : TftTouchShield.RED, TftTouchShield.WHITE);
        tft.print(storageReady ? "READY" : "UNAVAILABLE");
    }

    /** Clears a status row and puts the cursor where {@code characters} at the status size end up centered. */
    private void startRow(TftTouchShield tft, int y, int characters) {
        tft.fillRect(0, y, SCREEN_WIDTH, STATUS_ROW_HEIGHT, TftTouchShield.WHITE);
        tft.setTextSize(STATUS_TEXT_SIZE);
        tft.setCursor(Math.max(0, (SCREEN_WIDTH - characters * STATUS_CHAR_WIDTH) / 2), y);
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
}
