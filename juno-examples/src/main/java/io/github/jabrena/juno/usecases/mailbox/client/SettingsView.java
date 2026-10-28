package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.usecases.zelda.common.ux.Footer;
import io.github.jabrena.juno.util.mvc.KeyboardView;
import io.github.jabrena.juno.util.mvc.Navigator;

/**
 * Runtime editor for the mailbox API key, IP, and polling interval, opened from the cover before the first inbox
 * request. Tab moves between the three fields, and saving enters the same progress screen used by compose and refresh.
 * The local Wi-Fi IP is shown as secondary status information while the board joins the network.
 */
public final class SettingsView implements KeyboardView {
    private static final String LEGEND = "Enter: save | Tab: field";
    private static final int HOST_CAPACITY = 16;
    private static final int POLL_CAPACITY = 2;
    private static final int SETTINGS_TEXT_SIZE = 2;
    private static final int ESCAPE = 27;
    private static final int CARDPUTER_ESCAPE = '`';
    /** Each field is a small label beside a size-2 value; the value starts at {@link #VALUE_X} so 16 characters fit. */
    private static final int KEY_Y = 34;
    private static final int HOST_Y = 54;
    private static final int POLL_Y = 74;
    private static final int VALUE_X = 30;
    private static final int WIFI_Y = 92;
    private static final int MESSAGE_Y = 100;
    private static final int KEY_FIELD = 0;
    private static final int HOST_FIELD = 1;
    private static final int POLL_FIELD = 2;

    private final Navigator navigator;
    private final Display display;
    private final Keyboard keyboard;
    private final MailboxApi client;
    private final MailboxConnection connection;
    private final MailboxKey apiKey;
    private final MailboxPolling polling;
    private final ProgressView progress;
    private final Footer footer;
    private final char[] host = new char[HOST_CAPACITY];
    private int hostLength;
    private final char[] keyText = new char[MailboxKey.MAX_KEY_CHARS];
    private int keyLength;
    private final char[] pollingText = new char[POLL_CAPACITY];
    private int pollingLength;
    private int field = KEY_FIELD;
    private int drawnConnection = -1;
    public SettingsView(Navigator navigator, Display display, Keyboard keyboard, MailboxApi client,
            MailboxConnection connection, MailboxKey apiKey, MailboxPolling polling, ProgressView progress,
            Footer footer) {
        this.navigator = navigator;
        this.display = display;
        this.keyboard = keyboard;
        this.client = client;
        this.connection = connection;
        this.apiKey = apiKey;
        this.polling = polling;
        this.progress = progress;
        this.footer = footer;
        keyLength = apiKey.copy(keyText);
        pollingLength = polling.copy(pollingText);
    }

    @Override
    public void enter() {
        draw();
    }

    @Override
    public void render() {
        if (drawnConnection != connection.revision()) {
            drawWifi();
        }
    }

    @Override
    public void key(int key, boolean fnPressed) {
        if (keyboard.isControlPressed()) {
            return;
        } else if (key == Keyboard.TAB) {
            field = (field + 1) % 3;
            draw();
        } else if (key == Keyboard.BACKSPACE) {
            erase();
        } else if (key == Keyboard.ENTER) {
            save();
        } else if (key == ESCAPE || key == CARDPUTER_ESCAPE) {
            if (hostLength > 0) {
                save();
            }
        } else if (field == KEY_FIELD) {
            if (key > ' ' && key < 127 && keyLength < keyText.length) {
                keyText[keyLength] = (char) key;
                keyLength = keyLength + 1;
                draw();
            }
        } else if (field == HOST_FIELD && ((key >= '0' && key <= '9') || key == '.')) {
            if (hostLength < host.length) {
                host[hostLength] = (char) key;
                hostLength = hostLength + 1;
                draw();
            }
        } else if (field == POLL_FIELD && key >= '0' && key <= '9' && pollingLength < pollingText.length) {
            pollingText[pollingLength] = (char) key;
            pollingLength = pollingLength + 1;
            draw();
        }
    }

    private void erase() {
        if (field == KEY_FIELD && keyLength > 0) {
            keyLength = keyLength - 1;
            draw();
        } else if (field == HOST_FIELD && hostLength > 0) {
            hostLength = hostLength - 1;
            draw();
        } else if (field == POLL_FIELD && pollingLength > 0) {
            pollingLength = pollingLength - 1;
            draw();
        }
    }

    @Override
    public void touch(int x, int y) {
    }

    private void save() {
        if (hostLength == 0) {
            drawNote("Enter a mailbox IP", Display.RED);
            return;
        }
        if (keyLength == 0) {
            drawNote("Enter the mailbox API key", Display.RED);
            return;
        }
        if (!polling.change(pollingText, pollingLength)) {
            drawNote("Polling: enter 1-60 minutes", Display.RED);
            return;
        }
        if (!apiKey.change(keyText, keyLength)) {
            Serial.println("Mailbox client: API key not saved to SD");
        }
        StringBuilder value = new StringBuilder(HOST_CAPACITY);
        for (int index = 0; index < hostLength; index++) {
            value.append(host[index]);
        }
        client.host(value.toString());
        progress.refresh();
    }

    private void draw() {
        display.fillScreen(Display.BLACK);
        display.fillRect(0, 0, 240, 28, Display.BLUE);
        display.setTextColor(Display.WHITE, Display.BLUE);
        display.setTextSize(2);
        display.setCursor(8, 5);
        display.print("MAILBOX SETTINGS");
        drawField("IP", HOST_Y, host, hostLength, field == HOST_FIELD);
        drawField("Key", KEY_Y, keyText, keyLength, field == KEY_FIELD);
        drawField("min", POLL_Y, pollingText, pollingLength, field == POLL_FIELD);
        drawWifi();
        footer.draw(LEGEND);
    }

    /** The field being edited is yellow and ends in a cursor; the other is white. */
    private void drawField(String label, int y, char[] value, int length, boolean active) {
        display.setTextSize(1);
        display.setTextColor(Display.CYAN, Display.BLACK);
        display.setCursor(8, y + 4);
        display.print(label);
        display.setTextSize(SETTINGS_TEXT_SIZE);
        display.setTextColor(active ? Display.YELLOW : Display.WHITE, Display.BLACK);
        display.setCursor(VALUE_X, y);
        for (int index = 0; index < length; index++) {
            display.print(value[index]);
        }
        if (active) {
            display.print('_');
        }
    }

    /** One compact status row showing the local Wi-Fi address or connection state. */
    private void drawWifi() {
        drawnConnection = connection.revision();
        display.fillRect(0, WIFI_Y, 240, 10, Display.BLACK);
        display.setTextSize(1);
        display.setCursor(8, WIFI_Y);
        int state = connection.state();
        if (state == MailboxConnection.CONNECTED) {
            display.setTextColor(Display.GREEN, Display.BLACK);
            display.print("Local IP ");
            for (int index = 0; index < 4; index++) {
                if (index > 0) {
                    display.print('.');
                }
                printNumber(connection.address(index));
            }
        } else if (state == MailboxConnection.NO_SETTINGS) {
            display.setTextColor(Display.RED, Display.BLACK);
            display.print("WiFi: no SD settings");
        } else {
            display.setTextColor(Display.MAGENTA, Display.BLACK);
            display.print(state == MailboxConnection.SLOW ? "WiFi still joining" : "WiFi joining...");
        }
    }

    /** Prints an address octet (0-255) digit by digit, since the display prints only characters and text. */
    private void printNumber(int value) {
        if (value >= 100) {
            display.print((char) ('0' + value / 100));
        }
        if (value >= 10) {
            display.print((char) ('0' + value / 10 % 10));
        }
        display.print((char) ('0' + value % 10));
    }

    private void drawNote(String message, int color) {
        draw();
        display.setTextSize(1);
        display.setTextColor(color, Display.BLACK);
        display.setCursor(8, MESSAGE_Y);
        display.print(message);
    }
}
