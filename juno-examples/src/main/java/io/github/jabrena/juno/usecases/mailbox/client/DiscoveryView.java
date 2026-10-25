package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.usecases.zelda.common.ux.Footer;
import io.github.jabrena.juno.util.mvc.KeyboardView;
import io.github.jabrena.juno.util.mvc.Navigator;

/**
 * Finds the mailbox on the local network without typing its IP: asks each address of the Cardputer's own /24 for
 * {@code /actuator/info}, in order from .1 to .254. One address is probed per frame, so Esc still reaches the manual
 * settings at any time.
 */
public final class DiscoveryView implements KeyboardView {
    private static final int SETTINGS_VIEW = 0;
    private static final int INBOX_VIEW = 1;
    private static final int ESCAPE = 27;
    private static final int CARDPUTER_ESCAPE = '`';
    private static final int HEADER_HEIGHT = 28;
    private static final int CONTENT_HEIGHT = 107 - HEADER_HEIGHT;
    private static final int TEXT_SIZE = 2;
    private static final int LABEL_Y = 40;
    private static final int HOST_Y = 64;
    private static final int HINT_Y = 92;
    private static final int HOST_CAPACITY = 16;
    private static final int FIRST_HOST = 1;
    private static final int LAST_HOST = 254;

    private final Navigator navigator;
    private final Display display;
    private final MailboxConnection connection;
    private final MailboxApi client;
    private final SettingsView settings;
    private final Footer footer;
    private int attempt;
    private boolean waitingDrawn;

    public DiscoveryView(Navigator navigator, Display display, MailboxConnection connection, MailboxApi client,
            SettingsView settings, Footer footer) {
        this.navigator = navigator;
        this.display = display;
        this.connection = connection;
        this.client = client;
        this.settings = settings;
        this.footer = footer;
    }

    @Override
    public void enter() {
        attempt = 0;
        waitingDrawn = false;
        display.fillScreen(Display.BLACK);
        display.fillRect(0, 0, 240, HEADER_HEIGHT, Display.BLUE);
        display.setTextColor(Display.WHITE, Display.BLUE);
        display.setTextSize(TEXT_SIZE);
        display.setCursor(8, 6);
        display.print("FIND MAILBOX");
        footer.draw("Esc: enter the IP by hand");
    }

    @Override
    public void render() {
        if (connection.state() == MailboxConnection.NO_SETTINGS) {
            navigator.show(SETTINGS_VIEW);
            return;
        }
        if (!connection.isConnected()) {
            if (!waitingDrawn) {
                drawLines("Waiting for WiFi", "");
                waitingDrawn = true;
            }
            return;
        }
        int last = nextHost();
        if (last < 0) {
            Serial.println("Mailbox client: no mailbox found");
            settings.notFound();
            navigator.show(SETTINGS_VIEW);
            return;
        }
        String candidate = host(last);
        Serial.print("Mailbox client: probe ");
        Serial.println(candidate);
        drawLines("Looking at", candidate);
        if (client.probe(candidate)) {
            Serial.print("Mailbox client: found mailbox at ");
            Serial.println(candidate);
            settings.found(candidate);
            client.host(candidate);
            navigator.show(INBOX_VIEW);
        }
    }

    @Override
    public void key(int key, boolean fnPressed) {
        if (key == ESCAPE || key == CARDPUTER_ESCAPE) {
            navigator.show(SETTINGS_VIEW);
        }
    }

    @Override
    public void touch(int x, int y) {
    }

    /** Returns the next last octet to try, in order and skipping the Cardputer's own, or {@code -1} after .254. */
    private int nextHost() {
        int own = connection.address(3);
        while (FIRST_HOST + attempt <= LAST_HOST) {
            int candidate = FIRST_HOST + attempt;
            attempt = attempt + 1;
            if (candidate != own) {
                return candidate;
            }
        }
        return -1;
    }

    /** Builds "a.b.c.last" from the Cardputer's own network; used at once, as runtime Strings are not kept. */
    private String host(int last) {
        StringBuilder value = new StringBuilder(HOST_CAPACITY);
        for (int index = 0; index < 3; index++) {
            appendNumber(value, connection.address(index));
            value.append('.');
        }
        appendNumber(value, last);
        return value.toString();
    }

    private void appendNumber(StringBuilder value, int number) {
        if (number >= 100) {
            value.append((char) ('0' + number / 100));
        }
        if (number >= 10) {
            value.append((char) ('0' + number / 10 % 10));
        }
        value.append((char) ('0' + number % 10));
    }

    private void drawLines(String label, String candidate) {
        display.fillRect(0, HEADER_HEIGHT, 240, CONTENT_HEIGHT, Display.BLACK);
        display.setTextSize(TEXT_SIZE);
        display.setTextColor(Display.CYAN, Display.BLACK);
        display.setCursor(8, LABEL_Y);
        display.print(label);
        display.setTextColor(Display.YELLOW, Display.BLACK);
        display.setCursor(8, HOST_Y);
        display.print(candidate);
        display.setTextSize(1);
        display.setTextColor(Display.WHITE, Display.BLACK);
        display.setCursor(8, HINT_Y);
        display.print("Asking for /actuator/info");
    }
}
