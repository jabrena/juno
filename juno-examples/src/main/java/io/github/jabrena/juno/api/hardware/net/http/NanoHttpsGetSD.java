package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpClient;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Joins the Wi-Fi network named in {@code application.properties} on the microSD card of a Nano Connector Carrier,
 * then sends an HTTPS GET every ten seconds with the Nano ESP32's {@link HttpClient#requestHttps}. The host and the
 * request path both come from the card. The TLS client checks the server's chain against Mozilla's root certificates. Prints the
 * status, the body length and the first bytes of the body at 115200 baud.
 *
 * <p>The card needs {@code wifi.ssid}, {@code wifi.password}, {@code https.host} (a DNS name under 31 characters,
 * since it is a runtime {@code String}) and {@code https.path}, for example {@code https.host=api.open-meteo.com}
 * and {@code https.path=/v1/forecast?latitude=40.4168&longitude=-3.7038&current=temperature_2m}.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.http.NanoHttpsGetSD -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoHttpsGetSD {
    private static final Wifi WIFI = ArduinoNano.getWifi();
    private static final SdCard SD_CARD = new NanoConnectorCarrier().getSdCard();

    private static final int HTTPS_PORT = 443;
    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int MAX_PATH_BYTES = 255;
    private static final int PREVIEW_BYTES = 80;

    private NanoHttpsGetSD() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Settings settings = loadSettings();
        if (settings == null) {
            return;
        }
        if (!connectWifi(settings.ssid(), settings.password())) {
            return;
        }

        byte[] path = new byte[MAX_PATH_BYTES];
        int pathLength = copyPath(settings.path(), path);
        if (pathLength < 0) {
            Serial.println("https.path is too long");
            return;
        }
        byte[] noRequestHeaders = new byte[1];
        byte[] response = new byte[512];
        byte[] headers = new byte[256];
        int[] statusAndHeadersLength = new int[2];
        while (true) {
            int bodyLength = HttpClient.requestHttps(settings.host(), HTTPS_PORT, "GET", path, pathLength,
                    noRequestHeaders, 0, null, -1, response, response.length, headers, headers.length,
                    statusAndHeadersLength);
            Serial.print("status=");
            Serial.print(statusAndHeadersLength[0]);
            Serial.print(" body=");
            Serial.println(bodyLength);
            printPreview(response, bodyLength);
            Delay.millis(10000);
        }
    }

    private static boolean connectWifi(String ssid, String password) {
        WIFI.begin(ssid, password);
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("WiFi connection failed");
            return false;
        }
        Serial.println("WiFi connected");
        return true;
    }

    /** Copies the request path into {@code buffer} as ASCII; returns its length, or -1 when it does not fit. */
    private static int copyPath(String path, byte[] buffer) {
        if (path.length() > buffer.length) {
            return -1;
        }
        for (int i = 0; i < path.length(); i++) {
            buffer[i] = (byte) path.charAt(i);
        }
        return path.length();
    }

    private static void printPreview(byte[] body, int length) {
        Serial.print("body: ");
        for (int i = 0; i < length && i < PREVIEW_BYTES; i++) {
            Serial.print((char) body[i]);
        }
        Serial.println("");
    }

    private static Settings loadSettings() throws IOException {
        if (!SD_CARD.begin()) {
            Serial.println("SD card initialization failed");
            return null;
        }
        InputStream file = SD_CARD.open("application.properties");
        if (file == null) {
            Serial.println("application.properties not found");
            return null;
        }
        Properties properties = new Properties();
        properties.load(file);
        file.close();
        String ssid = properties.getProperty("wifi.ssid");
        String password = properties.getProperty("wifi.password");
        String host = properties.getProperty("https.host");
        String path = properties.getProperty("https.path");
        if (ssid == null || password == null || host == null || path == null) {
            Serial.println("wifi.ssid, wifi.password, https.host or https.path is missing");
            return null;
        }
        return new Settings(ssid, password, host, path);
    }

    private record Settings(String ssid, String password, String host, String path) {
    }
}
