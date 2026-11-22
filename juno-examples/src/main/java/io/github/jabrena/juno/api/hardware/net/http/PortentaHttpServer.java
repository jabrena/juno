package io.github.jabrena.juno.api.hardware.net.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.carrier.PortentaHatCarrier;
import io.github.jabrena.juno.hardware.device.tft.Mhs35TftDisplay;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.security.SecretStore;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static java.net.HttpURLConnection.HTTP_BAD_METHOD;
import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * {@link NanoHttpServerSD} on the Portenta H7 with the Portenta Hat Carrier and an MHS-3.5 TFT: joins the Wi-Fi network
 * whose {@code wifi.ssid} and {@code wifi.password} are in the board's SE050C2 secret store, then serves port 80 with
 * {@code GET /api/status} and {@code POST /api/led?state=on|off}. The TFT stands in for an LED: black when off, green
 * when on. The board's address is printed over Serial at 115200 baud every 10 seconds.
 *
 * <p>Store the credentials once with {@code juno:secret}; they survive reflashing:
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml juno:secret -Djuno.board=arduino-portenta-h7 -Djuno.port=<port> \
 *   -Djuno.secret.list=wifi.ssid,wifi.password
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.http.PortentaHttpServer -Djuno.board=arduino-portenta-h7
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-portenta-h7 -Djuno.baudRate=115200
 * curl http://<address>/api/status
 * curl -X POST 'http://<address>/api/led?state=on'
 * }</pre>
 */
@Board({ArduinoPortentaH7Board.class})
public final class PortentaHttpServer {
    private static boolean ledOn;

    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int HTTP_PORT = 80;
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_PROBLEM_JSON = "application/problem+json";

    private PortentaHttpServer() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Serial.println("HTTP: boot");

        PortentaHatCarrier carrier = new PortentaHatCarrier();
        Display display = new Mhs35TftDisplay(carrier);
        display.fillScreen(Display.BLACK);
        Wifi wifi = MCU.getWifi();
        if (!joinWifi(wifi, MCU.getSecretStore())) return;
        startServer(display);
        reportAlive(wifi);
    }

    /** Joins the network named in the secret store; {@code false} when that fails. */
    private static boolean joinWifi(Wifi wifi, SecretStore secrets) {
        String ssid = secrets.getString("wifi.ssid");
        String password = secrets.getString("wifi.password");
        if (ssid == null || password == null) {
            Serial.println("HTTP: wifi.ssid or wifi.password missing from the secret store");
            return false;
        }
        return connectWifi(wifi, ssid, password);
    }

    /** Registers the API handlers and starts listening on port 80. */
    private static void startServer(Display display) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 4);
        server.createContext("/api/status", PortentaHttpServer::status);
        server.createContext("/api/led", exchange -> setLed(exchange, display));
        server.createContext("/", PortentaHttpServer::notFound);
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

    private static void setLed(HttpExchange exchange, Display display) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            send(exchange, HTTP_BAD_METHOD, APPLICATION_PROBLEM_JSON, "{\"title\":\"method not allowed\"}");
            return;
        }
        String path = exchange.getRequestURI().toString();
        if (!path.equals("/api/led?state=on") && !path.equals("/api/led?state=off")) {
            send(exchange, HTTP_BAD_REQUEST, APPLICATION_PROBLEM_JSON, "{\"title\":\"invalid state\"}");
            return;
        }
        ledOn = path.equals("/api/led?state=on");
        display.fillScreen(ledOn ? Display.GREEN : Display.BLACK);
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
}
