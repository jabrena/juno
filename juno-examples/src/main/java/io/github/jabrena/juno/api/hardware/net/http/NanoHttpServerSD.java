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

import static java.net.HttpURLConnection.HTTP_BAD_METHOD;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * Joins the Wi-Fi network named in {@code application.properties} on the microSD card of a Nano Connector Carrier,
 * then serves port 80 with {@code GET /api/status} and {@code POST /api/led?state=on|off}. The board's address is
 * printed over Serial at 115200 baud every 10 seconds.
 *
 * <p>The card needs {@code wifi.ssid} and {@code wifi.password}. Flash it, read the address from the monitor, then
 * call it from a laptop on the same network:
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.http.NanoHttpServerSD -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * curl http://<address>/api/status
 * curl -X POST 'http://<address>/api/led?state=on'
 * }</pre>
 */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoHttpServerSD {
    private static boolean ledOn;

    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int HTTP_PORT = 80;
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_PROBLEM_JSON = "application/problem+json";

    private NanoHttpServerSD() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("HTTP: boot");

        Gpio gpio = MCU.getGpio();
        Wifi wifi = MCU.getWifi();
        SdCard sdCard = openSdCard(gpio);
        if (sdCard == null || !joinWifi(wifi, sdCard)) return;
        startServer(gpio);
        reportAlive(wifi);
    }

    /** Mounts the microSD card of the Nano Connector Carrier, or returns {@code null} when it is missing. */
    private static SdCard openSdCard(Gpio gpio) {
        Serial.println("HTTP: opening Nano Connector Carrier SD");
        SdCard sdCard = new NanoConnectorCarrier(gpio).getSdCard();
        if (!sdCard.begin()) {
            Serial.println("HTTP: Nano Connector Carrier SD initialization failed");
            return null;
        }
        Serial.println("HTTP: Nano Connector Carrier SD initialized");
        return sdCard;
    }

    /** Joins the network named in {@code application.properties}; {@code false} when that fails. */
    private static boolean joinWifi(Wifi wifi, SdCard sdCard) throws IOException {
        Credentials credentials = loadCredentials(sdCard);
        if (credentials == null) {
            Serial.println("HTTP: application.properties or Wi-Fi credentials unavailable");
            return false;
        }
        return connectWifi(wifi, credentials.ssid(), credentials.password());
    }

    /** Registers the API handlers and starts listening on port 80. */
    private static void startServer(Gpio gpio) throws IOException {
        int ledPin = gpio.getBuiltinLed();
        gpio.pinMode(ledPin, gpio.getOutput());
        gpio.digitalWrite(ledPin, false);
        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 4);
        server.createContext("/api/status", NanoHttpServerSD::status);
        server.createContext("/api/led", exchange -> setLed(exchange, gpio, ledPin));
        server.createContext("/", NanoHttpServerSD::notFound);
        server.start();
        Serial.println("Listening on port 80");
    }

    /** Prints the address every 10 seconds, so a monitor attached late still finds the server. */
    private static void reportAlive(Wifi wifi) {
        while (true) {
            printLocalIp(wifi);
            Serial.println("HTTP: server alive");
            Delay.millis(10000);
        }
    }

    private static void status(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET")) {
            send(exchange, HTTP_BAD_METHOD, APPLICATION_PROBLEM_JSON, "{\"title\":\"method not allowed\"}");
            return;
        }
        send(exchange, HTTP_OK, APPLICATION_JSON, ledOn ? "{\"led\":\"on\"}" : "{\"led\":\"off\"}");
    }

    private static void setLed(HttpExchange exchange, Gpio gpio, int ledPin) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, HTTP_BAD_METHOD, APPLICATION_PROBLEM_JSON, "{\"title\":\"method not allowed\"}");
            return;
        }
        String path = exchange.getRequestURI().toString();
        if (!path.equals("/api/led?state=on") && !path.equals("/api/led?state=off")) {
            send(exchange, HTTP_BAD_REQUEST, APPLICATION_PROBLEM_JSON, "{\"title\":\"state must be on or off\"}");
            return;
        }
        ledOn = path.equals("/api/led?state=on");
        gpio.digitalWrite(ledPin, ledOn);
        send(exchange, HTTP_OK, APPLICATION_JSON, ledOn ? "{\"led\":\"on\"}" : "{\"led\":\"off\"}");
    }

    private static void notFound(HttpExchange exchange) throws IOException {
        send(exchange, HTTP_NOT_FOUND, APPLICATION_PROBLEM_JSON, "{\"title\":\"not found\"}");
    }

    private static void send(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        exchange.getResponseHeaders().set(CONTENT_TYPE, contentType);
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
