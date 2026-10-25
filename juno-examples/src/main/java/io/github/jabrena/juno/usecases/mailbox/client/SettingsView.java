package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.usecases.zelda.common.ux.Footer;
import io.github.jabrena.juno.util.mvc.KeyboardView;
import io.github.jabrena.juno.util.mvc.Navigator;

/** Runtime mailbox-IP editor used before the first inbox request. */
public final class SettingsView implements KeyboardView {
    private static final int INBOX_VIEW = 1;
    private static final int HOST_CAPACITY = 16;
    private static final int SETTINGS_TEXT_SIZE = 2;
    private static final int ESCAPE = 27;
    private static final int CARDPUTER_ESCAPE = '`';

    private final Navigator navigator;
    private final Display display;
    private final MailboxClient client;
    private final Footer footer;
    private final char[] host = new char[HOST_CAPACITY];
    private int hostLength;

    public SettingsView(Navigator navigator, Display display, MailboxClient client, Footer footer, String initialHost) {
        this.navigator = navigator;
        this.display = display;
        this.client = client;
        this.footer = footer;
        if (initialHost != null) {
            hostLength = Math.min(initialHost.length(), host.length);
            for (int index = 0; index < hostLength; index++) {
                host[index] = initialHost.charAt(index);
            }
        }
    }

    @Override
    public void enter() {
        draw();
    }

    @Override
    public void render() {
    }

    @Override
    public void key(int key, boolean fnPressed) {
        if (key == Keyboard.BACKSPACE) {
            if (hostLength > 0) {
                hostLength = hostLength - 1;
                draw();
            }
        } else if (key == Keyboard.ENTER) {
            save();
        } else if (key == ESCAPE || key == CARDPUTER_ESCAPE) {
            if (hostLength > 0) {
                save();
            }
        } else if ((key >= '0' && key <= '9') || key == '.') {
            if (hostLength < host.length) {
                host[hostLength] = (char) key;
                hostLength = hostLength + 1;
                draw();
            }
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
        display.setTextSize(SETTINGS_TEXT_SIZE);
        display.setTextColor(Display.CYAN, Display.BLACK);
        display.setCursor(8, 45);
        display.print("Server IP");
        display.setTextColor(Display.YELLOW, Display.BLACK);
        display.setCursor(8, 70);
        for (int index = 0; index < hostLength; index++) {
            display.print(host[index]);
        }
        display.print('_');
        footer.draw("Enter: save | Backspace: erase");
    }

    private void drawError(String message) {
        draw();
        display.setTextColor(Display.RED, Display.BLACK);
        display.setCursor(8, 90);
        display.print(message);
    }
}
