package io.github.jabrena.juno.api.hardware.net.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Properties;

/**
 * Joins the Wi-Fi network named in {@code application.properties} on the microSD card of a Nano Connector Carrier,
 * prints the board's address, then serves port 80 with {@code GET /api/status} and {@code POST /api/led?state=on|off}. Requests are logged over Serial at 115200 baud. POST to {@code /api/led?state=on} or {@code /api/led?state=off} to change the example state.
 *
 * <p>The card needs {@code wifi.ssid} and {@code wifi.password}. Open {@code http://<address>/api/status} from a laptop on
 * the same network.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.http.NanoHttpServerSD -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoHttpServerSD {
    private static boolean ledOn;

    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int HTTP_PORT = 80;

    private NanoHttpServerSD() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("HTTP: boot");

        Wifi wifi = MCU.getWifi();
        Gpio gpio = MCU.getGpio();
        int ledPin = gpio.getBuiltinLed();
        Serial.println("HTTP: opening Nano Connector Carrier SD");
        SdCard sdCard = new NanoConnectorCarrier(gpio).getSdCard();
        if (!sdCard.begin()) {
            haltAndReport("HTTP: Nano Connector Carrier SD initialization failed");
            return;
        }
        Serial.println("HTTP: Nano Connector Carrier SD initialized");
        Credentials credentials = loadCredentials(sdCard);
        if (credentials == null) {
            haltAndReport("HTTP: application.properties or Wi-Fi credentials unavailable");
            return;
        }
        if (!connectWifi(wifi, credentials.ssid(), credentials.password())) {
            haltAndReport("HTTP: Wi-Fi connection failed");
            return;
        }
        gpio.pinMode(ledPin, gpio.getOutput());
        gpio.digitalWrite(ledPin, false);
        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 4);
        server.createContext("/api/status", NanoHttpServerSD::status);
        server.createContext("/api/led", exchange -> setLed(exchange, gpio, ledPin));
        server.createContext("/", NanoHttpServerSD::notFound);
        server.start();
        Serial.println("Listening on port 80");

        while (true) {
            printLocalIp(wifi);
            Serial.println("HTTP: server alive");
            Delay.millis(10000);
        }
    }

    private static void status(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, 405, "application/problem+json", "{\"title\":\"method not allowed\"}");
            return;
        }
        send(exchange, 200, "application/json", ledOn ? "{\"led\":\"on\"}" : "{\"led\":\"off\"}");
    }

    private static void setLed(HttpExchange exchange, Gpio gpio, int ledPin) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, 405, "application/problem+json", "{\"title\":\"method not allowed\"}");
            return;
        }
        String path = exchange.getRequestURI().toString();
        if (!path.equals("/api/led?state=on") && !path.equals("/api/led?state=off")) {
            send(exchange, 400, "application/problem+json", "{\"title\":\"state must be on or off\"}");
            return;
        }
        ledOn = path.equals("/api/led?state=on");
        gpio.digitalWrite(ledPin, ledOn);
        send(exchange, 200, "application/json", ledOn ? "{\"led\":\"on\"}" : "{\"led\":\"off\"}");
    }

    private static void notFound(HttpExchange exchange) throws IOException {
        send(exchange, 404, "application/problem+json", "{\"title\":\"not found\"}");
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length());
        try (OutputStream output = exchange.getResponseBody()) {
            for (int index = 0; index < body.length(); index++) {
                output.write(body.charAt(index));
            }
        }
    }

    private static boolean connectWifi(Wifi wifi, String ssid, String password) {
        wifi.begin(ssid, password);
        int attempts = 0;
        while (wifi.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Serial.print("WiFi status: ");
            Serial.println(wifi.status());
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (wifi.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("WiFi connection failed");
            return false;
        }
        Serial.println("WiFi connected");
        return true;
    }

    private static void haltAndReport(String message) {
        while (true) {
            Serial.println(message);
            Delay.millis(5000);
        }
    }

    private static void printLocalIp(Wifi wifi) {
        int[] octets = new int[4];
        wifi.localIP(octets);
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
