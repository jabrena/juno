package io.github.jabrena.juno.api.hardware.net.wifi;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

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
public final class WifiStatusSDTFT {
    private static final Wifi WIFI = ArduinoUno.getWifi();
    private static final SdCard SD_CARD = TftTouchShield.getSdCard();

    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    private static final int MARGIN = 10;
    private static final int STATE_Y = 48;
    private static final int SSID_Y = 90;
    private static final int STATUS_Y = 112;
    private static final int IP_Y = 134;
    private static final int GATEWAY_Y = 156;
    private static final int MAC_Y = 178;
    private static final int HINT_Y = 208;

    private static final String HEX = "0123456789ABCDEF";

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
        WIFI.begin(credentials.ssid(), credentials.password());

        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            showWifiStatus();
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        showWifiStatus();

        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
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
        int status = WIFI.status();
        Serial.print("WiFi status: ");
        Serial.println(status);

        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.CYAN, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, STATUS_Y);
        TftTouchShield.print("Status: ");
        TftTouchShield.print(status);
        TftTouchShield.print("   ");

        showAddresses();
    }

    /**
     * Shows the board's IP address, its gateway and its hardware (MAC) address, on the screen and on serial. They
     * are what to compare against the router's client list when a board cannot reach its peers: two boards with
     * the same IP, or a gateway outside the board's subnet, explain a lot.
     */
    private static void showAddresses() {
        int[] address = new int[4];
        WIFI.localIP(address);
        showAddress("IP:  ", address, IP_Y);
        WIFI.gatewayIP(address);
        showAddress("GW:  ", address, GATEWAY_Y);
        int[] mac = new int[6];
        WIFI.macAddress(mac);
        showMac(mac);
    }

    private static void showAddress(String label, int[] octets, int y) {
        Serial.print(label);
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, y);
        TftTouchShield.print(label);
        for (int i = 0; i < 4; i++) {
            if (i > 0) {
                Serial.print(".");
                TftTouchShield.print(".");
            }
            Serial.print(octets[i]);
            TftTouchShield.print("" + octets[i]);
        }
        Serial.println("");
        TftTouchShield.print("   ");
    }

    private static void showMac(int[] mac) {
        Serial.print("MAC: ");
        TftTouchShield.setTextSize(2);
        TftTouchShield.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        TftTouchShield.setCursor(MARGIN, MAC_Y);
        TftTouchShield.print("MAC: ");
        for (int i = 0; i < 6; i++) {
            if (i > 0) {
                Serial.print(":");
                TftTouchShield.print(":");
            }
            Serial.print(hexByte(mac[i]));
            TftTouchShield.print(hexByte(mac[i]));
        }
        Serial.println("");
    }

    private static String hexByte(int value) {
        return "" + HEX.charAt(value >>> 4 & 0xf) + HEX.charAt(value & 0xf);
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
