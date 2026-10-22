package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.Arduino;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpServer;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Joins the Wi-Fi network named in {@code application.properties} on the microSD card of a Nano Connector Carrier,
 * prints the board's address, then serves port 80: {@code GET /status} answers {@code online} and every other request
 * answers 404. Each request is printed over Serial at 115200 baud.
 *
 * <p>The card needs {@code wifi.ssid} and {@code wifi.password}. Open {@code http://<address>/status} from a laptop on
 * the same network.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.http.NanoHttpServerSD -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoNanoEsp32.class})
public final class NanoHttpServerSD {
    private static final Wifi WIFI = Arduino.getWifi();
    private static final SdCard SD_CARD = new NanoConnectorCarrier().getSdCard();

    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int HTTP_PORT = 80;

    private NanoHttpServerSD() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Credentials credentials = loadCredentials();
        if (credentials == null) {
            return;
        }
        if (!connectWifi(credentials.ssid(), credentials.password())) {
            return;
        }
        printLocalIp();

        HttpServer.begin(HTTP_PORT);
        Serial.println("Listening on port 80");

        byte[] body = new byte[64];
        while (true) {
            int bodyLength = HttpServer.accept(body, body.length);
            if (bodyLength >= 0) {
                String path = HttpServer.path();
                if (path.equals("/status")) {
                    HttpServer.respond(200, "text/plain", "online");
                    Serial.println("GET /status answered 200");
                } else {
                    HttpServer.respond(404, "text/plain", "not found");
                    Serial.println("request answered 404");
                }
            }
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

    private static void printLocalIp() {
        int[] octets = new int[4];
        WIFI.localIP(octets);
        Serial.print("address=");
        Serial.print(octets[0]);
        Serial.print(".");
        Serial.print(octets[1]);
        Serial.print(".");
        Serial.print(octets[2]);
        Serial.print(".");
        Serial.println(octets[3]);
    }

    private static Credentials loadCredentials() throws IOException {
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
        if (ssid == null || password == null) {
            Serial.println("WiFi credentials are missing");
            return null;
        }
        return new Credentials(ssid, password);
    }

    private record Credentials(String ssid, String password) {
    }
}
