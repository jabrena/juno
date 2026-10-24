package io.github.jabrena.juno.api.hardware.net.email;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.lcd.LcdKeypadShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.email.Pop3Client;
import io.github.jabrena.juno.hardware.net.email.Smtp;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * A small menu-driven client on the LCD Keypad Shield: a menu view (row 0 {@code "UP/DOWN
 * Select"}, row 1 cycling {@code Inbox}/{@code Send Email}/{@code About} with UP/DOWN, SELECT
 * confirms) leading to three destinations — Send Email (sends an "Arduino Hello World" message
 * with {@link Smtp#send} to the mailbox configured by {@code SMTP_HOST}/{@code
 * SMPT_USERNAME}/{@code SMTP_PASSWORD}, briefly shows the result, then returns to the menu on its
 * own), About (a static banner, any button returns to the menu), and Inbox — itself a submenu
 * (same UP/DOWN/SELECT scheme, plus LEFT to go back to the main menu) between two views: Count
 * (shows {@link Pop3Client#messageCount}, any button returns to the Inbox submenu) and List
 * (shows the {@code Subject} of up to the first {@link #LIST_MAX_MESSAGES} messages via {@link
 * Pop3Client#readSubject}, {@link #LIST_PAGE_SIZE} per screen, UP/DOWN to page and SELECT to go
 * back to the Inbox submenu).
 */
@Board(ArduinoUnoR4WiFiBoard.class)
public final class EmailClient {
    private static final Wifi WIFI = ArduinoUno.getWifi();

    private final LcdKeypadShield lcd = new LcdKeypadShield();
    private static final int POP3_PORT = 995;
    private static final int SMTP_PORT = 587;
    // See InboxCount's identical constants: Wifi#status can report STATUS_CONNECTED slightly
    // before the module's TCP/IP stack is actually ready, so a connection attempt right after
    // connecting can fail with -1 even though every later one succeeds.
    private static final int CONNECT_RETRY_ATTEMPTS = 5;
    private static final int CONNECT_RETRY_DELAY_SECONDS = 2;
    private static final int BUTTON_POLL_MILLIS = 50;
    private static final int BRIEF_DISPLAY_SECONDS = 2;

    private static final int MENU_INBOX = 0;
    private static final int MENU_SEND_EMAIL = 1;
    private static final int MENU_ABOUT = 2;
    private static final int MENU_OPTION_COUNT = 3;

    private static final int INBOX_MENU_COUNT = 0;
    private static final int INBOX_MENU_LIST = 1;
    private static final int INBOX_MENU_OPTION_COUNT = 2;
    private static final int INBOX_MENU_BACK = -1;

    private static final int LIST_MAX_MESSAGES = 10;
    private static final int LIST_PAGE_SIZE = 2;

    public static void main(String[] args) {
        new EmailClient().run();
    }

    private void run() {
        Serial.begin(BaudRate.BAUD_115200);

        lcd.begin();
        lcd.print("Connecting...");

        connectWifi();

        while (true) {
            int selected = menuView();
            if (selected == MENU_INBOX) {
                inboxView();
            } else if (selected == MENU_SEND_EMAIL) {
                sendEmailView();
            } else {
                aboutView();
            }
        }
    }

    /** Runs the menu until SELECT is pressed, returning the chosen {@code MENU_*} option. */
    private int menuView() {
        int selected = MENU_INBOX;
        int lastButton = LcdKeypadShield.NONE;
        showMenu(selected);

        while (true) {
            int button = lcd.readButton();
            if (button != lastButton) {
                if (button == LcdKeypadShield.UP) {
                    selected = (selected + MENU_OPTION_COUNT - 1) % MENU_OPTION_COUNT;
                    showMenu(selected);
                } else if (button == LcdKeypadShield.DOWN) {
                    selected = (selected + 1) % MENU_OPTION_COUNT;
                    showMenu(selected);
                } else if (button == LcdKeypadShield.SELECT) {
                    return selected;
                }
                lastButton = button;
            }
            Delay.millis(BUTTON_POLL_MILLIS);
        }
    }

    private void showMenu(int selected) {
        lcd.clear();
        lcd.print("UP/DOWN Select");
        lcd.setCursor(0, 1);
        if (selected == MENU_INBOX) {
            lcd.print("Inbox");
        } else if (selected == MENU_SEND_EMAIL) {
            lcd.print("Send Email");
        } else {
            lcd.print("About");
        }
    }

    /** Runs the Inbox submenu until LEFT returns to the main menu. */
    private void inboxView() {
        while (true) {
            int selected = inboxMenuView();
            if (selected == INBOX_MENU_BACK) {
                return;
            }
            if (selected == INBOX_MENU_COUNT) {
                inboxCountView();
            } else {
                inboxListView();
            }
        }
    }

    /** Like {@link #menuView}, plus LEFT to signal "back" via {@link #INBOX_MENU_BACK}. */
    private int inboxMenuView() {
        int selected = INBOX_MENU_COUNT;
        int lastButton = LcdKeypadShield.NONE;
        showInboxMenu(selected);

        while (true) {
            int button = lcd.readButton();
            if (button != lastButton) {
                if (button == LcdKeypadShield.UP) {
                    selected = (selected + INBOX_MENU_OPTION_COUNT - 1) % INBOX_MENU_OPTION_COUNT;
                    showInboxMenu(selected);
                } else if (button == LcdKeypadShield.DOWN) {
                    selected = (selected + 1) % INBOX_MENU_OPTION_COUNT;
                    showInboxMenu(selected);
                } else if (button == LcdKeypadShield.SELECT) {
                    return selected;
                } else if (button == LcdKeypadShield.LEFT) {
                    return INBOX_MENU_BACK;
                }
                lastButton = button;
            }
            Delay.millis(BUTTON_POLL_MILLIS);
        }
    }

    private void showInboxMenu(int selected) {
        lcd.clear();
        lcd.print("UP/DOWN Select");
        lcd.setCursor(0, 1);
        if (selected == INBOX_MENU_COUNT) {
            lcd.print("Count");
        } else {
            lcd.print("List");
        }
    }

    private void inboxCountView() {
        lcd.clear();
        int count = pollMessageCount();
        showRow(0, "Inbox: ", count);
        Serial.print("Inbox count: ");
        Serial.println(count);

        awaitAnyButtonPress();
    }

    /**
     * Pages through the {@code Subject} of up to the first {@link #LIST_MAX_MESSAGES} messages,
     * {@link #LIST_PAGE_SIZE} per screen (one per row). UP/DOWN moves between pages (clamped at
     * the first/last page); SELECT returns to the Inbox submenu — not "any button", since UP/DOWN
     * mean something different here than in every other view.
     */
    private void inboxListView() {
        int count = pollMessageCount();
        if (count < 0) {
            lcd.clear();
            showRow(0, "Inbox err ", count);
            Delay.seconds(BRIEF_DISPLAY_SECONDS);
            return;
        }
        int listed = count < LIST_MAX_MESSAGES ? count : LIST_MAX_MESSAGES;
        if (listed == 0) {
            lcd.clear();
            lcd.print("Inbox empty");
            Delay.seconds(BRIEF_DISPLAY_SECONDS);
            return;
        }

        int pageCount = (listed + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE;
        int page = 0;
        showListPage(page, listed);

        int lastButton = LcdKeypadShield.NONE;
        while (true) {
            int button = lcd.readButton();
            if (button != lastButton) {
                if (button == LcdKeypadShield.UP && page > 0) {
                    page = page - 1;
                    showListPage(page, listed);
                } else if (button == LcdKeypadShield.DOWN && page < pageCount - 1) {
                    page = page + 1;
                    showListPage(page, listed);
                } else if (button == LcdKeypadShield.SELECT) {
                    return;
                }
                lastButton = button;
            }
            Delay.millis(BUTTON_POLL_MILLIS);
        }
    }

    private void showListPage(int page, int listed) {
        lcd.clear();
        int firstMessageNumber = page * LIST_PAGE_SIZE + 1;
        showSubjectRow(0, firstMessageNumber, listed);
        showSubjectRow(1, firstMessageNumber + 1, listed);
    }

    /** Shows message {@code messageNumber}'s subject on {@code row}, or nothing past {@code listed}. */
    private void showSubjectRow(int row, int messageNumber, int listed) {
        if (messageNumber > listed) {
            return;
        }
        lcd.setCursor(0, row);
        byte[] subject = new byte[LcdKeypadShield.COLUMNS];
        int length = Pop3Client.readSubject(
                System.getenv("SMTP_HOST"), POP3_PORT,
                System.getenv("SMPT_USERNAME"), System.getenv("SMTP_PASSWORD"),
                messageNumber, subject, subject.length);
        if (length < 0) {
            lcd.print("err ");
            lcd.print(length);
            return;
        }
        lcd.print(subject, length);
    }

    private void sendEmailView() {
        lcd.clear();
        lcd.print("Sending...");

        int sent = Smtp.send(
                System.getenv("SMTP_HOST"), SMTP_PORT,
                System.getenv("SMPT_USERNAME"), System.getenv("SMTP_PASSWORD"),
                System.getenv("SMPT_USERNAME"), System.getenv("SMPT_USERNAME"),
                "Arduino Hello World", "Hello World from Juno on the Arduino UNO R4 WiFi.");
        Serial.print("Smtp.send: ");
        Serial.println(sent);

        lcd.setCursor(0, 1);
        if (sent == 0) {
            lcd.print("Sent!");
        } else {
            lcd.print("Send err ");
            lcd.print(sent);
        }
        Delay.seconds(BRIEF_DISPLAY_SECONDS);
    }

    private void aboutView() {
        lcd.clear();
        lcd.print("Arduino UNO R4");
        lcd.setCursor(0, 1);
        lcd.print("Email Client");

        awaitAnyButtonPress();
    }

    /** Waits for a button to go from not-pressed to pressed, debounced by {@link #BUTTON_POLL_MILLIS}. */
    private void awaitAnyButtonPress() {
        int lastButton = lcd.readButton();
        while (true) {
            int button = lcd.readButton();
            if (button != LcdKeypadShield.NONE && button != lastButton) {
                return;
            }
            lastButton = button;
            Delay.millis(BUTTON_POLL_MILLIS);
        }
    }

    private int pollMessageCount() {
        int count = -1;
        for (int attempt = 0; attempt < CONNECT_RETRY_ATTEMPTS; attempt++) {
            count = Pop3Client.messageCount(
                    System.getenv("SMTP_HOST"), POP3_PORT,
                    System.getenv("SMPT_USERNAME"), System.getenv("SMTP_PASSWORD"));
            if (count != -1) {
                return count;
            }
            Delay.seconds(CONNECT_RETRY_DELAY_SECONDS);
        }
        return count;
    }

    private void showRow(int row, String label, int value) {
        lcd.setCursor(0, row);
        lcd.print(label);
        lcd.print(value);
        lcd.print("     ");
    }

    private void connectWifi() {
        Serial.println("Starting WiFi");
        WIFI.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        while (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Delay.millis(500);
        }
        Serial.println("WiFi connected");
    }
}
