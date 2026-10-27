package io.github.jabrena.juno.usecases.lichess;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpClient;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** The network model service for the public Lichess puzzle endpoints. */
public final class LichessApi {
    private static final int WIFI_ATTEMPTS = 30;
    private static final int RESPONSE_CAPACITY = 2048;
    private static final int RESPONSE_HEADERS_CAPACITY = 256;

    private final Wifi wifi = ArduinoUno.getWifi();
    private final byte[] response = new byte[RESPONSE_CAPACITY];
    private final byte[] responseHeaders = new byte[RESPONSE_HEADERS_CAPACITY];
    private final int[] status = new int[2];

    /** Reads wifi.ssid and wifi.password from the TFT shield's application.properties and joins that network. */
    public boolean connect(SdCard sdCard) {
        if (!sdCard.begin()) {
            Serial.println("Lichess: SD card unavailable");
            return false;
        }
        try (InputStream file = sdCard.open("application.properties")) {
            if (file == null) {
                Serial.println("Lichess: application.properties not found");
                return false;
            }
            Properties properties = new Properties();
            properties.load(file);
            String ssid = properties.getProperty("wifi.ssid");
            String password = properties.getProperty("wifi.password");
            if (ssid == null || password == null) {
                Serial.println("Lichess: WiFi settings are missing");
                return false;
            }
            wifi.begin(ssid, password);
        } catch (IOException failed) {
            Serial.println("Lichess: could not read application.properties");
            return false;
        }
        for (int attempt = 0; wifi.status() != Wifi.STATUS_CONNECTED && attempt < WIFI_ATTEMPTS; attempt++) {
            Delay.millis(1000);
        }
        if (wifi.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("Lichess: WiFi connection failed");
            return false;
        }
        return true;
    }

    /** Downloads the current daily puzzle. */
    public boolean loadDaily(LichessModel model) {
        int length = HttpClient.get("https://lichess.org/api/puzzle/daily", response, response.length,
                responseHeaders, responseHeaders.length, status);
        return model.loadResponse(response, length, status[0], LichessModel.DAILY);
    }

    /** Downloads a public fresh puzzle, useful when the player wants another challenge. */
    public boolean loadFresh(LichessModel model) {
        int length = HttpClient.get("https://lichess.org/api/puzzle/next", response, response.length,
                responseHeaders, responseHeaders.length, status);
        return model.loadResponse(response, length, status[0], LichessModel.FRESH);
    }
}
