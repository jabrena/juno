package io.github.jabrena.juno.api.hardware.net;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.Esp32Cam;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Connects the AI Thinker ESP32-CAM to the network named in the microSD card's application.properties file. */
@Board(AiThinkerEsp32CamBoard.class)
public final class AiThinkerEsp32CamWifiFromSd {
    private static final String CREDENTIALS_FILE = "application.properties";
    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final Wifi WIFI = Esp32Cam.getWifi();
    private static final SdCard SD_CARD = Esp32Cam.getSdCard();
    private static final int[] LOCAL_IP = new int[4];

    private AiThinkerEsp32CamWifiFromSd() {
    }

    /** Reads {@code wifi.ssid} and {@code wifi.password}, joins the network, and prints its IPv4 address. */
    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        if (!SD_CARD.begin()) {
            Serial.println("SD card initialization failed");
            return;
        }

        String ssid;
        String password;
        try (InputStream file = SD_CARD.open(CREDENTIALS_FILE)) {
            if (file == null) {
                Serial.println("application.properties not found");
                return;
            }
            Properties properties = new Properties();
            properties.load(file);
            ssid = properties.getProperty("wifi.ssid");
            password = properties.getProperty("wifi.password");
        }

        if (ssid == null || password == null) {
            Serial.println("WiFi credentials are missing");
            return;
        }

        WIFI.begin(ssid, password);
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }

        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Serial.print("WiFi connection failed; status=");
            Serial.println(WIFI.status());
            return;
        }

        WIFI.localIP(LOCAL_IP);
        Serial.print("WiFi connected; IP=");
        Serial.print(LOCAL_IP[0]);
        Serial.print(".");
        Serial.print(LOCAL_IP[1]);
        Serial.print(".");
        Serial.print(LOCAL_IP[2]);
        Serial.print(".");
        Serial.println(LOCAL_IP[3]);
    }
}
