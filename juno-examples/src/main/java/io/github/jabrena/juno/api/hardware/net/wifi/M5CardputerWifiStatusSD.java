package io.github.jabrena.juno.api.hardware.net.wifi;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.M5StackCardputer;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Connects to the Wi-Fi network configured on the Cardputer's SD card and shows its status on the built-in display. */
@Board(M5StackCardputerBoard.class)
public final class M5CardputerWifiStatusSD {
    private static final Wifi WIFI = M5StackCardputer.getWifi();
    private static final SdCard SD_CARD = M5StackCardputer.getSd();
    private static final Display DISPLAY = M5StackCardputer.getDisplay();

    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int MARGIN = 8;
    private static final int STATE_Y = 36;
    private static final int SSID_Y = 68;
    private static final int STATUS_Y = 96;

    private M5CardputerWifiStatusSD() {
    }

    /** Loads credentials from {@code application.properties}, joins the network and refreshes the screen status. */
    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);

        DISPLAY.begin();
        showHeader();

        WifiCredentials credentials = loadCredentials();
        if (credentials == null) {
            showState("SD card error", Display.RED);
            return;
        }

        showSsid(credentials.ssid());
        showState("Connecting", Display.YELLOW);
        Serial.println("Starting WiFi from SD properties");
        WIFI.begin(credentials.ssid(), credentials.password());

        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            showWifiStatus();
            Delay.millis(1000);
            attempts = attempts + 1;
        }

        if (WIFI.status() == Wifi.STATUS_CONNECTED) {
            showState("Connected", Display.GREEN);
        } else {
            showState("Connection failed", Display.RED);
        }

        while (true) {
            showWifiStatus();
            Delay.millis(1000);
        }
    }

    private static void showHeader() {
        DISPLAY.fillScreen(Display.BLACK);
        DISPLAY.fillRect(0, 0, DISPLAY.width(), 28, Display.BLUE);
        DISPLAY.setTextColor(Display.WHITE, Display.BLUE);
        DISPLAY.setTextSize(2);
        DISPLAY.setCursor(MARGIN, 5);
        DISPLAY.print("WiFi connection");
    }

    private static void showState(String state, int color) {
        DISPLAY.fillRect(0, STATE_Y, DISPLAY.width(), 26, Display.BLACK);
        DISPLAY.setTextColor(color, Display.BLACK);
        DISPLAY.setTextSize(2);
        DISPLAY.setCursor(MARGIN, STATE_Y);
        DISPLAY.print(state);
    }

    private static void showSsid(String ssid) {
        DISPLAY.setTextColor(Display.WHITE, Display.BLACK);
        DISPLAY.setTextSize(1);
        DISPLAY.setCursor(MARGIN, SSID_Y);
        DISPLAY.print("SSID: ");
        DISPLAY.print(ssid);
    }

    private static void showWifiStatus() {
        int status = WIFI.status();
        Serial.print("WiFi status: ");
        Serial.println(status);

        DISPLAY.fillRect(0, STATUS_Y, DISPLAY.width(), 20, Display.BLACK);
        DISPLAY.setTextColor(Display.CYAN, Display.BLACK);
        DISPLAY.setTextSize(2);
        DISPLAY.setCursor(MARGIN, STATUS_Y);
        DISPLAY.print("Status: ");
        DISPLAY.print("" + status);
    }

    private static WifiCredentials loadCredentials() throws IOException {
        if (!SD_CARD.begin()) {
            Serial.println("SD card initialization failed");
            return null;
        }

        try (InputStream file = SD_CARD.open("application.properties")) {
            if (file == null) {
                Serial.println("application.properties not found");
                return null;
            }

            Properties properties = new Properties();
            properties.load(file);

            String ssid = properties.getProperty("wifi.ssid");
            String password = properties.getProperty("wifi.password");
            if (ssid == null || password == null) {
                Serial.println("WiFi credentials are missing");
                return null;
            }

            return new WifiCredentials(ssid, password);
        }
    }

    private record WifiCredentials(String ssid, String password) {
    }
}
