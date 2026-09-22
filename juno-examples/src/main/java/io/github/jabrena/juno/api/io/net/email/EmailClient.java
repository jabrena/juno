package io.github.jabrena.juno.api.io.net.email;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.net.Wifi;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;
import io.github.jabrena.juno.api.lcd.LcdKeypadShield;

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
@Board(ArduinoUnoR4WiFi.class)
public final class EmailClient {
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
        Serial.begin(BaudRate.BAUD_115200);

        LcdKeypadShield.begin();
        LcdKeypadShield.print("Connecting...");

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
    private static int menuView() {
        int selected = MENU_INBOX;
        int lastButton = LcdKeypadShield.NONE;
        showMenu(selected);

        while (true) {
            int button = LcdKeypadShield.readButton();
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

    private static void showMenu(int selected) {
        LcdKeypadShield.clear();
        LcdKeypadShield.print("UP/DOWN Select");
        LcdKeypadShield.setCursor(0, 1);
        if (selected == MENU_INBOX) {
            LcdKeypadShield.print("Inbox");
        } else if (selected == MENU_SEND_EMAIL) {
            LcdKeypadShield.print("Send Email");
        } else {
            LcdKeypadShield.print("About");
        }
    }

    /** Runs the Inbox submenu until LEFT returns to the main menu. */
    private static void inboxView() {
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
    private static int inboxMenuView() {
        int selected = INBOX_MENU_COUNT;
        int lastButton = LcdKeypadShield.NONE;
        showInboxMenu(selected);

        while (true) {
            int button = LcdKeypadShield.readButton();
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

    private static void showInboxMenu(int selected) {
        LcdKeypadShield.clear();
        LcdKeypadShield.print("UP/DOWN Select");
        LcdKeypadShield.setCursor(0, 1);
        if (selected == INBOX_MENU_COUNT) {
            LcdKeypadShield.print("Count");
        } else {
            LcdKeypadShield.print("List");
        }
    }

    private static void inboxCountView() {
        LcdKeypadShield.clear();
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
    private static void inboxListView() {
        int count = pollMessageCount();
        if (count < 0) {
            LcdKeypadShield.clear();
            showRow(0, "Inbox err ", count);
            Delay.seconds(BRIEF_DISPLAY_SECONDS);
            return;
        }
        int listed = count < LIST_MAX_MESSAGES ? count : LIST_MAX_MESSAGES;
        if (listed == 0) {
            LcdKeypadShield.clear();
            LcdKeypadShield.print("Inbox empty");
            Delay.seconds(BRIEF_DISPLAY_SECONDS);
            return;
        }

        int pageCount = (listed + LIST_PAGE_SIZE - 1) / LIST_PAGE_SIZE;
        int page = 0;
        showListPage(page, listed);

        int lastButton = LcdKeypadShield.NONE;
        while (true) {
            int button = LcdKeypadShield.readButton();
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

    private static void showListPage(int page, int listed) {
        LcdKeypadShield.clear();
        int firstMessageNumber = page * LIST_PAGE_SIZE + 1;
        showSubjectRow(0, firstMessageNumber, listed);
        showSubjectRow(1, firstMessageNumber + 1, listed);
    }

    /** Shows message {@code messageNumber}'s subject on {@code row}, or nothing past {@code listed}. */
    private static void showSubjectRow(int row, int messageNumber, int listed) {
        if (messageNumber > listed) {
            return;
        }
        LcdKeypadShield.setCursor(0, row);
        byte[] subject = new byte[LcdKeypadShield.COLUMNS];
        int length = Pop3Client.readSubject(
                System.getenv("SMTP_HOST"), POP3_PORT,
                System.getenv("SMPT_USERNAME"), System.getenv("SMTP_PASSWORD"),
                messageNumber, subject, subject.length);
        if (length < 0) {
            LcdKeypadShield.print("err ");
            LcdKeypadShield.print(length);
            return;
        }
        LcdKeypadShield.print(subject, length);
    }

    private static void sendEmailView() {
        LcdKeypadShield.clear();
        LcdKeypadShield.print("Sending...");

        int sent = Smtp.send(
                System.getenv("SMTP_HOST"), SMTP_PORT,
                System.getenv("SMPT_USERNAME"), System.getenv("SMTP_PASSWORD"),
                System.getenv("SMPT_USERNAME"), System.getenv("SMPT_USERNAME"),
                "Arduino Hello World", "Hello World from Juno on the Arduino UNO R4 WiFi.");
        Serial.print("Smtp.send: ");
        Serial.println(sent);

        LcdKeypadShield.setCursor(0, 1);
        if (sent == 0) {
            LcdKeypadShield.print("Sent!");
        } else {
            LcdKeypadShield.print("Send err ");
            LcdKeypadShield.print(sent);
        }
        Delay.seconds(BRIEF_DISPLAY_SECONDS);
    }

    private static void aboutView() {
        LcdKeypadShield.clear();
        LcdKeypadShield.print("Arduino One R4");
        LcdKeypadShield.setCursor(0, 1);
        LcdKeypadShield.print("Email Client");

        awaitAnyButtonPress();
    }

    /** Waits for a button to go from not-pressed to pressed, debounced by {@link #BUTTON_POLL_MILLIS}. */
    private static void awaitAnyButtonPress() {
        int lastButton = LcdKeypadShield.readButton();
        while (true) {
            int button = LcdKeypadShield.readButton();
            if (button != LcdKeypadShield.NONE && button != lastButton) {
                return;
            }
            lastButton = button;
            Delay.millis(BUTTON_POLL_MILLIS);
        }
    }

    private static int pollMessageCount() {
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

    private static void showRow(int row, String label, int value) {
        LcdKeypadShield.setCursor(0, row);
        LcdKeypadShield.print(label);
        LcdKeypadShield.print(value);
        LcdKeypadShield.print("     ");
    }

    private static void connectWifi() {
        Serial.println("Starting WiFi");
        Wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        while (Wifi.status() != Wifi.STATUS_CONNECTED) {
            Delay.millis(500);
        }
        Serial.println("WiFi connected");
    }
}
