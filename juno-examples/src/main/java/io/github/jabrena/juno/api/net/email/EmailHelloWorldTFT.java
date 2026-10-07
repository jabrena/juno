package io.github.jabrena.juno.api.net.email;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.net.Wifi;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Touch-screen version of {@link EmailHelloWorldLCD}. After Wi-Fi connects, a tap sends a
 * plain-text message over implicit TLS and verifies delivery by polling the POP3S inbox count.
 * Progress and the final result are shown on the ELEGOO 2.8" TFT touch shield and logged to
 * Serial. On UNO Q, Linux must already be connected to Wi-Fi.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class EmailHelloWorldTFT {
    private static final int POP3_PORT = 995;
    private static final int SMTP_PORT = 465;
    private static final int CONNECT_RETRY_ATTEMPTS = 5;
    private static final int CONNECT_RETRY_DELAY_SECONDS = 2;
    private static final int DELIVERY_POLL_ATTEMPTS = 12;
    private static final int DELIVERY_POLL_DELAY_SECONDS = 5;
    private static final int TOUCH_POLL_MILLIS = 50;

    private static final int MARGIN = 12;
    private static final int HEADER_HEIGHT = 42;
    private static final int STATUS_Y = 62;
    private static final int BEFORE_Y = 104;
    private static final int SEND_Y = 140;
    private static final int AFTER_Y = 176;
    private static final int HINT_Y = 214;

    private EmailHelloWorldTFT() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        drawFrame();

        showStatus("Connecting WiFi", TftTouchShield.YELLOW);
        connectWifi();
        showPrompt();
        waitForTap();
        sendAndVerify();

        while (true) {
            Delay.seconds(1);
        }
    }

    private static void drawFrame() {
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        TftTouchShield.fillRect(0, 0, TftTouchShield.width(), HEADER_HEIGHT, TftTouchShield.NAVY);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(MARGIN, 12);
        TftTouchShield.print("Email Hello World");
    }

    private static void showPrompt() {
        clearBody();
        showStatus("Ready to send", TftTouchShield.GREEN);
        showLine(HINT_Y, "Tap screen to send", TftTouchShield.CYAN);
    }

    private static void sendAndVerify() {
        clearBody();
        showStatus("Checking inbox...", TftTouchShield.YELLOW);
        int before = pollMessageCount();
        showValue(BEFORE_Y, "Before: ", before, TftTouchShield.WHITE);
        Serial.print("Inbox count before send: ");
        Serial.println(before);

        showLine(SEND_Y, "Sending...", TftTouchShield.YELLOW);
        int sent = Smtp.sendTls(
                System.getenv("SMTP_HOST"), SMTP_PORT,
                System.getenv("SMPT_USERNAME"), System.getenv("SMTP_PASSWORD"),
                System.getenv("SMPT_USERNAME"), System.getenv("SMPT_USERNAME"),
                "Hello World", "Hello World");
        Serial.print("Smtp.sendTls: ");
        Serial.println(sent);

        if (sent != 0) {
            showValue(SEND_Y, "Send error: ", sent, TftTouchShield.RED);
            showStatus("Delivery failed", TftTouchShield.RED);
            return;
        }

        showLine(SEND_Y, "Sent, verifying...", TftTouchShield.CYAN);
        int after = awaitDelivery(before);
        showValue(AFTER_Y, "After: ", after, TftTouchShield.WHITE);
        if (after > before) {
            showStatus("Delivery confirmed", TftTouchShield.GREEN);
        } else {
            showStatus("Delivery not seen", TftTouchShield.ORANGE);
        }
    }

    private static int awaitDelivery(int before) {
        int after = before;
        for (int attempt = 0; attempt < DELIVERY_POLL_ATTEMPTS; attempt++) {
            Delay.seconds(DELIVERY_POLL_DELAY_SECONDS);
            after = Pop3Client.messageCount(
                    System.getenv("SMTP_HOST"), POP3_PORT,
                    System.getenv("SMPT_USERNAME"), System.getenv("SMTP_PASSWORD"));
            Serial.print("Inbox count after send: ");
            Serial.println(after);
            showValue(AFTER_Y, "Checking: ", after, TftTouchShield.WHITE);
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

    private static void connectWifi() {
        Serial.println("Starting WiFi");
        Wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        while (Wifi.status() != Wifi.STATUS_CONNECTED) {
            Delay.millis(500);
        }
        Serial.println("WiFi connected");
    }

    private static void waitForTap() {
        while (!TftTouchShield.readTouch()) {
            Delay.millis(TOUCH_POLL_MILLIS);
        }
        while (TftTouchShield.readTouch()) {
            Delay.millis(TOUCH_POLL_MILLIS);
        }
    }

    private static void clearBody() {
        TftTouchShield.fillRect(0, HEADER_HEIGHT, TftTouchShield.width(),
                TftTouchShield.height() - HEADER_HEIGHT, TftTouchShield.BLACK);
    }

    private static void showStatus(String text, int color) {
        TftTouchShield.fillRect(0, STATUS_Y, TftTouchShield.width(), 28, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, STATUS_Y);
        TftTouchShield.print(text);
    }

    private static void showLine(int y, String text, int color) {
        TftTouchShield.fillRect(0, y, TftTouchShield.width(), 22, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, y);
        TftTouchShield.print(text);
    }

    private static void showValue(int y, String label, int value, int color) {
        TftTouchShield.fillRect(0, y, TftTouchShield.width(), 22, TftTouchShield.BLACK);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(color, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, y);
        TftTouchShield.print(label);
        TftTouchShield.print(value);
    }
}
