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
        TftTouchShield tft = new TftTouchShield();
        Serial.begin(BaudRate.BAUD_115200);

        tft.begin();
        tft.setRotation(TftTouchShield.LANDSCAPE);
        tft.fillScreen(TftTouchShield.BLACK);
        tft.fillRect(0, 0, tft.width(), 40, TftTouchShield.NAVY);
        tft.setTextSize(3);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.NAVY);
        tft.setCursor(MARGIN, 8);
        tft.print("WiFi connection");

        while (!connect(tft)) {
            showHint(tft, "Tap screen to retry");
            waitForTap(tft);
            showHint(tft, "                   ");
        }

        showState(tft, "Connected ", TftTouchShield.GREEN);
        while (true) {
            showWifiStatus(tft);
            Delay.millis(1000);
        }
    }

    private static boolean connect(TftTouchShield tft) throws IOException {
        showState(tft, "Connecting", TftTouchShield.YELLOW);

        WifiCredentials credentials = loadCredentials(tft.getSdCard());
        if (credentials == null) {
            showState(tft, "Error     ", TftTouchShield.RED);
            return false;
        }

        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        tft.setCursor(MARGIN, SSID_Y);
        tft.print("SSID: ");
        tft.print(credentials.ssid());

        Serial.println("Starting WiFi from SD properties");
        WIFI.begin(credentials.ssid(), credentials.password());

        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            showWifiStatus(tft);
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        showWifiStatus(tft);

        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("WiFi connection failed");
            showState(tft, "Error     ", TftTouchShield.RED);
            return false;
        }
        return true;
    }

    private static void showState(TftTouchShield tft, String state, int color) {
        tft.setTextSize(4);
        tft.setTextColor(color, TftTouchShield.BLACK);
        tft.setCursor(MARGIN, STATE_Y);
        tft.print(state);
    }

    private static void showWifiStatus(TftTouchShield tft) {
        int status = WIFI.status();
        Serial.print("WiFi status: ");
        Serial.println(status);

        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.CYAN, TftTouchShield.BLACK);
        tft.setCursor(MARGIN, STATUS_Y);
        tft.print("Status: ");
        tft.print(status);
        tft.print("   ");

        showAddresses(tft);
    }

    /**
     * Shows the board's IP address, its gateway and its hardware (MAC) address, on the screen and on serial. They
     * are what to compare against the router's client list when a board cannot reach its peers: two boards with
     * the same IP, or a gateway outside the board's subnet, explain a lot.
     */
    private static void showAddresses(TftTouchShield tft) {
        int[] address = new int[4];
        WIFI.localIP(address);
        showAddress(tft, "IP:  ", address, IP_Y);
        WIFI.gatewayIP(address);
        showAddress(tft, "GW:  ", address, GATEWAY_Y);
        int[] mac = new int[6];
        WIFI.macAddress(mac);
        showMac(tft, mac);
    }

    private static void showAddress(TftTouchShield tft, String label, int[] octets, int y) {
        Serial.print(label);
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        tft.setCursor(MARGIN, y);
        tft.print(label);
        for (int i = 0; i < 4; i++) {
            if (i > 0) {
                Serial.print(".");
                tft.print(".");
            }
            Serial.print(octets[i]);
            tft.print("" + octets[i]);
        }
        Serial.println("");
        tft.print("   ");
    }

    private static void showMac(TftTouchShield tft, int[] mac) {
        Serial.print("MAC: ");
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.WHITE, TftTouchShield.BLACK);
        tft.setCursor(MARGIN, MAC_Y);
        tft.print("MAC: ");
        for (int i = 0; i < 6; i++) {
            if (i > 0) {
                Serial.print(":");
                tft.print(":");
            }
            Serial.print(hexByte(mac[i]));
            tft.print(hexByte(mac[i]));
        }
        Serial.println("");
    }

    private static String hexByte(int value) {
        return "" + HEX.charAt(value >>> 4 & 0xf) + HEX.charAt(value & 0xf);
    }

    private static void showHint(TftTouchShield tft, String hint) {
        tft.setTextSize(2);
        tft.setTextColor(TftTouchShield.ORANGE, TftTouchShield.BLACK);
        tft.setCursor(MARGIN, HINT_Y);
        tft.print(hint);
    }

    private static void waitForTap(TftTouchShield tft) {
        while (!tft.readTouch()) {
            Delay.millis(50);
        }
        Serial.print("Touch at ");
        Serial.print(tft.touchX());
        Serial.print(",");
        Serial.println(tft.touchY());
        while (tft.readTouch()) {
            Delay.millis(50);
        }
    }

    private static WifiCredentials loadCredentials(SdCard sdCard) throws IOException {
        if (!sdCard.begin()) {
            Serial.println("SD card initialization failed");
            return null;
        }

        try (InputStream file = sdCard.open("application.properties")) {
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
