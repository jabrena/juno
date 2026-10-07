package io.github.jabrena.juno.api.net;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.SdCard;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.tft.TftTouchShield;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The ELEGOO 2.8" TFT touch screen version of
 * {@link WifiStatusSD}: loads WiFi credentials from
 * {@code application.properties} on the shield's own microSD socket (chip select on D10), connects,
 * and shows the connection state on the TFT in landscape.
 *
 * <p>The screen shows a {@code WiFi connection} header, a colored state line ({@code Connecting},
 * {@code Connected}, or {@code Error}), the SSID being used, and the numeric value returned by
 * {@link Wifi#status()}, refreshed once per second. When loading the credentials or connecting
 * fails, the screen asks for a tap; touching it reloads the card and tries again.
 *
 * <p>Unlike the LCD Keypad Shield, this shield shares no pins with its SD socket, so no rewiring
 * is needed.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class WifiStatusTFT {
    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    private static final int MARGIN = 10;
    private static final int STATE_Y = 60;
    private static final int SSID_Y = 110;
    private static final int STATUS_Y = 140;
    private static final int HINT_Y = 200;

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);

        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        TftTouchShield.fillScreen(TftTouchShield.BLACK);
        TftTouchShield.fillRect(0, 0, TftTouchShield.width(), 40, TftTouchShield.NAVY);
        TftTouchShield.setTextSize(3);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        TftTouchShield.setCursor(MARGIN, 8);
        TftTouchShield.print("WiFi connection");

        while (!connect()) {
            showHint("Tap screen to retry");
            waitForTap();
            showHint("                   ");
        }

        showState("Connected ", TftTouchShield.GREEN);
        while (true) {
            showWifiStatus();
            Delay.millis(1000);
        }
    }

    private static boolean connect() throws IOException {
        showState("Connecting", TftTouchShield.YELLOW);

        WifiCredentials credentials = loadCredentials();
        if (credentials == null) {
            showState("Error     ", TftTouchShield.RED);
            return false;
        }

        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, SSID_Y);
        TftTouchShield.print("SSID: ");
        TftTouchShield.print(credentials.ssid());

        Serial.println("Starting WiFi from SD properties");
        Wifi.begin(credentials.ssid(), credentials.password());

        int attempts = 0;
        while (Wifi.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            showWifiStatus();
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        showWifiStatus();

        if (Wifi.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("WiFi connection failed");
            showState("Error     ", TftTouchShield.RED);
            return false;
        }
        return true;
    }

    private static void showState(String state, int color) {
        TftTouchShield.setTextSize(4);
        TftTouchShield.setTextColor(color, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, STATE_Y);
        TftTouchShield.print(state);
    }

    private static void showWifiStatus() {
        int status = Wifi.status();
        Serial.print("WiFi status: ");
        Serial.println(status);

        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, STATUS_Y);
        TftTouchShield.print("Status: ");
        TftTouchShield.print(status);
        TftTouchShield.print("   ");
    }

    private static void showHint(String hint) {
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.ORANGE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, HINT_Y);
        TftTouchShield.print(hint);
    }

    private static void waitForTap() {
        while (!TftTouchShield.readTouch()) {
            Delay.millis(50);
        }
        Serial.print("Touch at ");
        Serial.print(TftTouchShield.touchX());
        Serial.print(",");
        Serial.println(TftTouchShield.touchY());
        while (TftTouchShield.readTouch()) {
            Delay.millis(50);
        }
    }

    private static WifiCredentials loadCredentials() throws IOException {
        if (!SdCard.begin()) {
            Serial.println("SD card initialization failed");
            return null;
        }

        InputStream file = SdCard.open("application.properties");
        if (file == null) {
            Serial.println("application.properties not found");
            return null;
        }

        Properties properties = new Properties();
        properties.load(file);
        file.close();

        String ssid = properties.getProperty("wifi.ssid");
        String password = properties.getProperty("wifi.password");
        if (ssid == null || password == null) {
            Serial.println("WiFi credentials are missing");
            return null;
        }

        return new WifiCredentials(ssid, password);
    }

    private record WifiCredentials(String ssid, String password) {
    }
}
