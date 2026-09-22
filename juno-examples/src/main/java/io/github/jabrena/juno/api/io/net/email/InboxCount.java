package io.github.jabrena.juno.api.io.net.email;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.net.Wifi;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;
import io.github.jabrena.juno.api.lcd.LcdKeypadShield;

/**
 * Connects to WiFi and the mailbox configured by {@code SMTP_HOST}/{@code SMPT_USERNAME}/{@code
 * SMTP_PASSWORD} (the same account {@link Smtp} would send from — POP3S on {@link #POP3_PORT}
 * needs no separate credentials), polls {@link Pop3Client#messageCount} every {@link
 * #POLL_INTERVAL_SECONDS} seconds, and shows the current message count on the LCD Keypad Shield's first
 * row ({@code "Inbox: N"}), or {@code "Inbox: err N"} for a negative result — see {@link
 * Pop3Client#messageCount}'s Javadoc for what each error code means. Every poll is also logged to
 * Serial.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class InboxCount {
    private static final int POP3_PORT = 995;
    private static final int POLL_INTERVAL_SECONDS = 30;
    // Wifi#status can report STATUS_CONNECTED slightly before the module's TCP/IP stack (DHCP
    // lease, routing) is actually ready to accept an outbound connection, so a connection attempt
    // right after connecting can fail with -1 even though every later one succeeds. Retrying a
    // few times, a couple of seconds apart, absorbs that one-off race instead of surfacing it.
    private static final int CONNECT_RETRY_ATTEMPTS = 5;
    private static final int CONNECT_RETRY_DELAY_SECONDS = 2;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        LcdKeypadShield.begin();
        LcdKeypadShield.print("Connecting...");

        connectWifi();

        LcdKeypadShield.clear();
        LcdKeypadShield.print("Inbox: ?");

        while (true) {
            showCount(pollMessageCount());
            Delay.seconds(POLL_INTERVAL_SECONDS);
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

    private static void showCount(int count) {
        LcdKeypadShield.setCursor(0, 0);
        if (count >= 0) {
            LcdKeypadShield.print("Inbox: ");
            LcdKeypadShield.print(count);
            LcdKeypadShield.print("     ");
            Serial.print("Inbox count: ");
            Serial.println(count);
        } else {
            LcdKeypadShield.print("Inbox: err ");
            LcdKeypadShield.print(count);
            Serial.print("Inbox count failed: ");
            Serial.println(count);
        }
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
