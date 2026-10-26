package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.usecases.zelda.common.ux.Footer;
import io.github.jabrena.juno.util.mvc.KeyboardView;
import io.github.jabrena.juno.util.mvc.Navigator;

/**
 * Runtime editor for the mailbox IP and API key, used before the first inbox request; Tab moves between the two
 * fields. It also follows the background Wi-Fi join.
 */
public final class SettingsView implements KeyboardView {
    private static final int INBOX_VIEW = 1;
    private static final int DISCOVERY_VIEW = 5;
    private static final String LEGEND = "Enter: save | Tab: field | Ctrl+f: find";
    private static final int HOST_CAPACITY = 16;
    private static final int SETTINGS_TEXT_SIZE = 2;
    private static final int ESCAPE = 27;
    private static final int CARDPUTER_ESCAPE = '`';
    /** Each field is a small label beside a size-2 value; the value starts at {@link #VALUE_X} so 16 characters fit. */
    private static final int HOST_Y = 34;
    private static final int KEY_Y = 54;
    private static final int VALUE_X = 30;
    private static final int WIFI_Y = 74;
    private static final int MESSAGE_Y = 96;

    private final Navigator navigator;
    private final Display display;
    private final Keyboard keyboard;
    private final MailboxApi client;
    private final MailboxConnection connection;
    private final MailboxKey apiKey;
    private final Footer footer;
    private final char[] host = new char[HOST_CAPACITY];
    private int hostLength;
    private final char[] keyText = new char[MailboxKey.MAX_KEY_CHARS];
    private int keyLength;
    private boolean editingKey;
    private int drawnConnection = -1;
    private boolean notFound;

    public SettingsView(Navigator navigator, Display display, Keyboard keyboard, MailboxApi client,
            MailboxConnection connection, MailboxKey apiKey, Footer footer) {
        this.navigator = navigator;
        this.display = display;
        this.keyboard = keyboard;
        this.client = client;
        this.connection = connection;
        this.apiKey = apiKey;
        this.footer = footer;
    }

    @Override
    public void enter() {
        keyLength = apiKey.copy(keyText);
        if (notFound) {
            drawError("Mailbox not found; enter its IP");
        } else {
            draw();
        }
    }

    /** Discovery found the mailbox here; the field shows it in case the user later opens the settings. */
    public void found(String value) {
        notFound = false;
        hostLength = 0;
        for (int index = 0; index < value.length() && index < host.length; index++) {
            host[index] = value.charAt(index);
            hostLength = hostLength + 1;
        }
    }

    /** Discovery asked every address and none answered as the mailbox. */
    public void notFound() {
        notFound = true;
    }

    @Override
    public void render() {
        if (drawnConnection != connection.revision()) {
            drawWifi();
        }
    }

    @Override
    public void key(int key, boolean fnPressed) {
        notFound = false;
        if (keyboard.isControlPressed()) {
            if (key == 'f' || key == 'F') {
                navigator.show(DISCOVERY_VIEW);
            }
        } else if (key == Keyboard.TAB) {
            editingKey = !editingKey;
            draw();
        } else if (key == Keyboard.BACKSPACE) {
            erase();
        } else if (key == Keyboard.ENTER) {
            save();
        } else if (key == ESCAPE || key == CARDPUTER_ESCAPE) {
            if (hostLength > 0) {
                save();
            }
        } else if (editingKey) {
            if (key > ' ' && key < 127 && keyLength < keyText.length) {
                keyText[keyLength] = (char) key;
                keyLength = keyLength + 1;
                draw();
            }
        } else if ((key >= '0' && key <= '9') || key == '.') {
            if (hostLength < host.length) {
                host[hostLength] = (char) key;
                hostLength = hostLength + 1;
                draw();
            }
        }
    }

    private void erase() {
        if (editingKey && keyLength > 0) {
            keyLength = keyLength - 1;
            draw();
        } else if (!editingKey && hostLength > 0) {
            hostLength = hostLength - 1;
            draw();
        }
    }

    @Override
    public void touch(int x, int y) {
    }

    private void save() {
        if (hostLength == 0) {
            drawError("Enter a mailbox IP");
            return;
        }
        if (keyLength == 0) {
            drawError("Enter the mailbox API key");
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
        navigator.show(INBOX_VIEW);
    }

    private void draw() {
        display.fillScreen(Display.BLACK);
        display.fillRect(0, 0, 240, 28, Display.BLUE);
        display.setTextColor(Display.WHITE, Display.BLUE);
        display.setTextSize(2);
        display.setCursor(8, 5);
        display.print("MAILBOX SETTINGS");
        drawField("IP", HOST_Y, host, hostLength, !editingKey);
        drawField("Key", KEY_Y, keyText, keyLength, editingKey);
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

    /** One size-2 row, so at most 20 characters: "WiFi 192.168.100.100" just fits. */
    private void drawWifi() {
        drawnConnection = connection.revision();
        display.fillRect(0, WIFI_Y, 240, 16, Display.BLACK);
        display.setTextSize(SETTINGS_TEXT_SIZE);
        display.setCursor(8, WIFI_Y);
        int state = connection.state();
        if (state == MailboxConnection.CONNECTED) {
            display.setTextColor(Display.GREEN, Display.BLACK);
            display.print("WiFi ");
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

    private void drawError(String message) {
        draw();
        display.setTextSize(1);
        display.setTextColor(Display.RED, Display.BLACK);
        display.setCursor(8, MESSAGE_Y);
        display.print(message);
    }
}
