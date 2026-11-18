package io.github.jabrena.juno.usecases.mailbox.server.cover;

import io.github.jabrena.juno.hardware.device.tft.Mhs35TftDisplay;
import io.github.jabrena.juno.usecases.mailbox.server.security.MailboxSecurity;
import io.github.jabrena.juno.usecases.mailbox.server.security.MailboxSecurityView;

/**
 * The mailbox TFT view, in portrait: a flat blue mailbox with a letter dropping into its slot, above the message
 * count, IP address, and HTTPS state, and a settings gear that opens a status screen with the SD-card, Wi-Fi and
 * API-key state.
 * From there the Security screen changes the API key on a numeric keypad. Later changes redraw only their own row on
 * the cover, or the whole status screen while it is shown.
 */
public final class MailboxView {
    public static final int BLUE = 0x3C1E;
    static final int MOTION = 0x5ACB;
    public static final int TEXT = 0x19D1;
    public static final int DARK_GREEN = 0x0400;
    /** RGB565 orange, which the MHS-3.5 display does not name. */
    public static final int ORANGE = 0xFD20;

    /** The cover's three status rows (message count, IP address, then HTTPS) share text size 2 and are each centered. */
    public static final int SCREEN_WIDTH = 240;
    public static final int SCREEN_HEIGHT = 320;
    private static final int STATUS_TEXT_SIZE = 2;
    private static final int STATUS_CHAR_WIDTH = STATUS_TEXT_SIZE * Mhs35TftDisplay.CHAR_WIDTH;
    private static final int STATUS_ROW_HEIGHT = STATUS_TEXT_SIZE * Mhs35TftDisplay.CHAR_HEIGHT;
    private static final int COUNT_Y = 232;
    private static final int IP_Y = 256;
    private static final int HTTPS_Y = 272;
    private static final String HTTPS_LABEL = "HTTPS: ";
    private static final String MESSAGES_LABEL = "MESSAGES: ";
    private static final String IP_HINT = "WAITING FOR WI-FI";
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

    public static final int HEADER_HEIGHT = 44;
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
    private int internetColor = ORANGE;
    // The IP is kept as text bytes, not as the String it came in: a runtime String's slot is reused later.
    private final byte[] ip = new byte[IP_CAPACITY];
    private int ipLength;
    private boolean ipMissing = true;
    private int screen = COVER;
    private final MailboxSecurity security;
    private final MailboxSecurityView securityView;
    private final MailboxArt art = new MailboxArt();

    public MailboxView(MailboxSecurity security) {
        this.security = security;
        this.securityView = new MailboxSecurityView(security);
    }

    public void drawCover(Mhs35TftDisplay tft, boolean storageReady, int messageCount) {
        this.storageReady = storageReady;
        this.messageCount = messageCount;
        drawCoverScreen(tft);
    }

    /** Publishes SD readiness without redrawing the mailbox art while startup work is still active. */
    public void updateStorageReady(Mhs35TftDisplay tft, boolean storageReady) {
        this.storageReady = storageReady;
        if (screen == STATUS) {
            drawStorageStatus(tft);
        }
    }

    public void drawInternetConnecting(Mhs35TftDisplay tft) {
        drawInternet(tft, "CONNECTING", ORANGE);
    }

    /** The board joined the network and was given an address. */
    public void drawInternetOnline(Mhs35TftDisplay tft) {
        drawInternet(tft, "ONLINE", DARK_GREEN);
    }

    public void drawInternetOffline(Mhs35TftDisplay tft) {
        drawInternet(tft, "OFFLINE", Mhs35TftDisplay.RED);
    }

    public void drawMessageCount(Mhs35TftDisplay tft, int messageCount) {
        this.messageCount = messageCount;
        if (screen == STATUS) {
            drawStatusScreen(tft);
        } else if (screen == COVER) {
            drawCountRow(tft);
        }
    }

    public void drawIp(Mhs35TftDisplay tft, int[] octets) {
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

    /**
     * A tap on the cover's gear opens the status screen. There, SECURITY opens the Security screen and any other tap
     * goes back to the cover; on the Security screen the keypad edits the key, and the header goes back to the status
     * screen without changing it.
     */
    public void touch(Mhs35TftDisplay tft, int x, int y) {
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

    private void drawInternet(Mhs35TftDisplay tft, String state, int color) {
        internetState = state;
        internetColor = color;
        if (screen == STATUS) {
            drawStatusScreen(tft);
        } else if (screen == COVER) {
            drawInternetRow(tft);
        }
    }

    /** The cover's third row shows the HTTPS listener state without hiding the configured IP. */
    private void drawInternetRow(Mhs35TftDisplay tft) {
        startRow(tft, HTTPS_Y, HTTPS_LABEL.length() + internetState.length());
        tft.setTextColor(TEXT, Mhs35TftDisplay.WHITE);
        tft.print(HTTPS_LABEL);
        tft.setTextColor(internetColor, Mhs35TftDisplay.WHITE);
        tft.print(internetState);
    }

    private void redrawIp(Mhs35TftDisplay tft) {
        if (screen == STATUS) {
            drawStatusScreen(tft);
        } else if (screen == COVER) {
            drawIpRow(tft);
        }
    }

    private void drawCoverScreen(Mhs35TftDisplay tft) {
        tft.fillScreen(Mhs35TftDisplay.WHITE);
        art.draw(tft);
        drawCountRow(tft);
        drawIpRow(tft);
        drawInternetRow(tft);
        drawKeyHint(tft);
        drawGear(tft);
    }

    /** While the API key is still the default, the cover says so, so changing it is the obvious first step. */
    private void drawKeyHint(Mhs35TftDisplay tft) {
        if (!security.isDefault()) {
            return;
        }
        tft.setTextSize(1);
        tft.setCursor(8, KEY_HINT_Y);
        tft.setTextColor(Mhs35TftDisplay.RED, Mhs35TftDisplay.WHITE);
        tft.print(DEFAULT_KEY_HINT);
    }

    private void drawCountRow(Mhs35TftDisplay tft) {
        startRow(tft, COUNT_Y, MESSAGES_LABEL.length() + digits(messageCount));
        tft.setTextColor(TEXT, Mhs35TftDisplay.WHITE);
        tft.print(MESSAGES_LABEL);
        tft.setTextColor(BLUE, Mhs35TftDisplay.WHITE);
        tft.print(messageCount);
    }

    /** The hint shown until Wi-Fi gives the board an address is too long for the row, so it uses the small font. */
    private void drawIpRow(Mhs35TftDisplay tft) {
        if (ipMissing) {
            tft.fillRect(0, IP_Y, SCREEN_WIDTH, STATUS_ROW_HEIGHT, Mhs35TftDisplay.WHITE);
            tft.setTextSize(1);
            tft.setCursor((SCREEN_WIDTH - IP_HINT.length() * Mhs35TftDisplay.CHAR_WIDTH) / 2,
                    IP_Y + Mhs35TftDisplay.CHAR_HEIGHT / 2);
            tft.setTextColor(TEXT, Mhs35TftDisplay.WHITE);
            tft.print(IP_HINT);
            return;
        }
        startRow(tft, IP_Y, ipLength);
        tft.setTextColor(TEXT, Mhs35TftDisplay.WHITE);
        printIp(tft);
    }

    private void drawGear(Mhs35TftDisplay tft) {
        for (int tooth = 0; tooth < GEAR_TOOTH_X.length; tooth++) {
            tft.fillCircle(GEAR_X + GEAR_TOOTH_X[tooth], GEAR_Y + GEAR_TOOTH_Y[tooth], GEAR_TOOTH_RADIUS,
                    TEXT);
        }
        tft.fillCircle(GEAR_X, GEAR_Y, GEAR_RADIUS, TEXT);
        tft.fillCircle(GEAR_X, GEAR_Y, GEAR_TOOTH_RADIUS, Mhs35TftDisplay.WHITE);
    }

    /** SD card, network, IP, message count and API key, one labelled row each, above the BACK and SECURITY buttons. */
    private void drawStatusScreen(Mhs35TftDisplay tft) {
        tft.fillScreen(Mhs35TftDisplay.WHITE);
        drawHeader(tft, "STATUS");

        drawStorageStatus(tft);
        statusLabel(tft, "NET", 1);
        tft.setTextColor(internetColor, Mhs35TftDisplay.WHITE);
        tft.print(internetState);
        statusLabel(tft, "IP", 2);
        tft.setTextColor(BLUE, Mhs35TftDisplay.WHITE);
        if (ipMissing) {
            tft.print("NO WI-FI");
        } else {
            printIp(tft);
        }
        statusLabel(tft, "MAIL", 3);
        tft.setTextColor(BLUE, Mhs35TftDisplay.WHITE);
        tft.print(messageCount);
        statusLabel(tft, "KEY", 4);
        tft.setTextColor(security.isDefault() ? Mhs35TftDisplay.RED : DARK_GREEN, Mhs35TftDisplay.WHITE);
        tft.print(security.isDefault() ? "DEFAULT" : "SET");

        tft.setTextSize(STATUS_TEXT_SIZE);
        tft.setTextColor(MOTION, Mhs35TftDisplay.WHITE);
        tft.setCursor(LABEL_X, BACK_Y);
        tft.print("BACK");
        tft.fillRect(SECURITY_BUTTON_LEFT, BUTTON_TOP, SCREEN_WIDTH - SECURITY_BUTTON_LEFT, 40, BLUE);
        tft.setTextColor(Mhs35TftDisplay.WHITE, BLUE);
        tft.setCursor(SECURITY_BUTTON_LEFT + 12, BACK_Y);
        tft.print("SECURITY");
    }

    /** The blue title bar the status and Security screens share. */
    public static void drawHeader(Mhs35TftDisplay tft, String title) {
        tft.fillRect(0, 0, SCREEN_WIDTH, HEADER_HEIGHT, BLUE);
        tft.setTextSize(3);
        tft.setTextColor(Mhs35TftDisplay.WHITE, BLUE);
        tft.setCursor((SCREEN_WIDTH - title.length() * 3 * Mhs35TftDisplay.CHAR_WIDTH) / 2, 10);
        tft.print(title);
    }

    private void statusLabel(Mhs35TftDisplay tft, String label, int row) {
        int y = FIRST_ROW_Y + row * ROW_GAP;
        tft.setTextSize(STATUS_TEXT_SIZE);
        tft.setTextColor(TEXT, Mhs35TftDisplay.WHITE);
        tft.setCursor(LABEL_X, y);
        tft.print(label);
        tft.setCursor(VALUE_X, y);
    }

    private void drawStorageStatus(Mhs35TftDisplay tft) {
        tft.fillRect(VALUE_X, FIRST_ROW_Y, SCREEN_WIDTH - VALUE_X, STATUS_ROW_HEIGHT,
                Mhs35TftDisplay.WHITE);
        statusLabel(tft, "SD", 0);
        tft.setTextColor(storageReady ? DARK_GREEN : Mhs35TftDisplay.RED, Mhs35TftDisplay.WHITE);
        tft.print(storageReady ? "READY" : "UNAVAILABLE");
    }

    /** Clears a status row and puts the cursor where {@code characters} at the status size end up centered. */
    private void startRow(Mhs35TftDisplay tft, int y, int characters) {
        tft.fillRect(0, y, SCREEN_WIDTH, STATUS_ROW_HEIGHT, Mhs35TftDisplay.WHITE);
        tft.setTextSize(STATUS_TEXT_SIZE);
        tft.setCursor(Math.max(0, (SCREEN_WIDTH - characters * STATUS_CHAR_WIDTH) / 2), y);
    }

    /** The address one character at a time: the display prints characters and text, not raw bytes. */
    private void printIp(Mhs35TftDisplay tft) {
        for (int index = 0; index < ipLength; index++) {
            tft.print((char) ip[index]);
        }
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
