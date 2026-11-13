package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpMethod;
import io.github.jabrena.juno.hardware.net.http.HttpServer;
import io.github.jabrena.juno.hardware.net.http.HttpStatus;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Joins the Wi-Fi network named in {@code application.properties} on the microSD card of the ELEGOO TFT Touch Shield,
 * prints the board's address, then serves port 80 on the UNO WiFi Rev2: {@code GET /status} answers a JSON body and
 * every other request answers 404. Each request is printed over Serial at 115200 baud.
 *
 * <p>The card needs {@code wifi.ssid} and {@code wifi.password}, as in {@link NanoHttpServerSD}. Open
 * {@code http://<address>/status} from a laptop on the same network, or with {@code curl http://<address>/status}.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.http.HttpServerStatusSDTFT -Djuno.board=arduino-uno-r3-wifi-rev2
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-uno-r3-wifi-rev2
 * }</pre>
 */
@Board({ArduinoUnoR3WiFiRev2Board.class})
public final class HttpServerStatusSDTFT {
    private static final Wifi WIFI = MCU.getWifi();

    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int HTTP_PORT = 80;
    private static final String STATUS_BODY = "{\"status\":\"online\"}";

    private HttpServerStatusSDTFT() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        TftTouchShield tft = new TftTouchShield();
        Credentials credentials = loadCredentials(tft.getSdCard());
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
                String method = HttpServer.method();
                String path = HttpServer.path();
                if (method.equals(HttpMethod.GET) && path.equals("/status")) {
                    HttpServer.respond(HttpStatus.OK, "application/json", STATUS_BODY);
                    Serial.println("GET /status answered 200");
                } else {
                    HttpServer.respond(HttpStatus.NOT_FOUND, "text/plain", "not found");
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

    private static Credentials loadCredentials(SdCard sdCard) throws IOException {
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
            return new Credentials(ssid, password);
        }
    }

    private record Credentials(String ssid, String password) {
    }
}
