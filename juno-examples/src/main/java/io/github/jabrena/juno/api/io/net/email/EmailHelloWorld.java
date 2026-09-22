package io.github.jabrena.juno.api.io.net.email;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.net.Wifi;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;
import io.github.jabrena.juno.api.lcd.LcdKeypadShield;

/**
 * End-to-end proof that {@link Smtp#send} actually delivers, not just that the mail server
 * accepted the message. Two views on the LCD Keypad Shield: a prompt view that connects to WiFi
 * and then waits for any button press, and a send-and-verify view — triggered by that press —
 * which reads the inbox message count with {@link Pop3Client#messageCount} <em>before</em>
 * sending, sends a "Hello World" message to the mailbox configured by {@code SMTP_HOST}/{@code
 * SMPT_USERNAME}/{@code SMTP_PASSWORD} with {@link Smtp#send}, then polls the count <em>after</em>
 * sending — every {@link #DELIVERY_POLL_DELAY_SECONDS} seconds, up to {@link
 * #DELIVERY_POLL_ATTEMPTS} times — until it goes up. Every step is logged to Serial, including
 * whether delivery was actually confirmed or the poll gave up waiting.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class EmailHelloWorld {
    private static final int POP3_PORT = 995;
    private static final int SMTP_PORT = 587;
    // See InboxCount's identical constants: Wifi#status can report STATUS_CONNECTED slightly
    // before the module's TCP/IP stack is actually ready, so a connection attempt right after
    // connecting can fail with -1 even though every later one succeeds.
    private static final int CONNECT_RETRY_ATTEMPTS = 5;
    private static final int CONNECT_RETRY_DELAY_SECONDS = 2;
    private static final int DELIVERY_POLL_ATTEMPTS = 12;
    private static final int DELIVERY_POLL_DELAY_SECONDS = 5;
    private static final int BUTTON_POLL_MILLIS = 50;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        LcdKeypadShield.begin();
        LcdKeypadShield.print("Connecting...");

        connectWifi();

        showPromptView();
        awaitAnyButtonPress();

        sendAndVerifyView();
    }

    private static void showPromptView() {
        LcdKeypadShield.clear();
        LcdKeypadShield.print("Ready to send");
        LcdKeypadShield.setCursor(0, 1);
        LcdKeypadShield.print("Press any key");
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

    private static void sendAndVerifyView() {
        LcdKeypadShield.clear();
        int before = pollMessageCount();
        showRow(0, "Before: ", before);
        Serial.print("Inbox count before send: ");
        Serial.println(before);

        LcdKeypadShield.setCursor(0, 1);
        LcdKeypadShield.print("Sending...");
        int sent = Smtp.send(
                System.getenv("SMTP_HOST"), SMTP_PORT,
                System.getenv("SMPT_USERNAME"), System.getenv("SMTP_PASSWORD"),
                System.getenv("SMPT_USERNAME"), System.getenv("SMPT_USERNAME"),
                "Hello World", "Hello World");
        Serial.print("Smtp.send: ");
        Serial.println(sent);

        if (sent != 0) {
            showRow(1, "Send err ", sent);
            return;
        }

        int after = awaitDelivery(before);
        showRow(1, "After: ", after);
    }

    /** Polls {@link Pop3Client#messageCount} until it exceeds {@code before}, or gives up. */
    private static int awaitDelivery(int before) {
        int after = before;
        for (int attempt = 0; attempt < DELIVERY_POLL_ATTEMPTS; attempt++) {
            Delay.seconds(DELIVERY_POLL_DELAY_SECONDS);
            after = Pop3Client.messageCount(
                    System.getenv("SMTP_HOST"), POP3_PORT,
                    System.getenv("SMPT_USERNAME"), System.getenv("SMTP_PASSWORD"));
            Serial.print("Inbox count after send: ");
            Serial.println(after);
            if (after > before) {
                Serial.println("Delivery confirmed");
                return after;
            }
        }
        Serial.println("Gave up waiting for delivery");
        return after;
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
