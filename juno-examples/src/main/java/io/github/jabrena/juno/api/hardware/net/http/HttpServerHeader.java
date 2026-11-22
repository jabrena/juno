package io.github.jabrena.juno.api.hardware.net.http;


import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * Reads a request header with the JDK {@code HttpServer}: connects to WiFi with credentials from the board's secret
 * store, prints its {@link Wifi#localIP}, then serves {@code GET /hello} on port 80. The reply greets the caller by the
 * {@code X-Client-Name} header ({@code {"hello":"<name>"}}), read with
 * {@code exchange.getRequestHeaders().getFirst("X-Client-Name")}; a request without it gets a {@code 400} RFC 7807
 * problem detail, and any other route a {@code 404}. Try it with {@code curl -H "X-Client-Name: Ada" http://<ip>/hello}.
 *
 * <p>Quotes and backslashes are dropped from the echoed value to keep the JSON body valid.
 */
public final class HttpServerHeader {
    private static final Wifi WIFI = MCU.getWifi();

    public static void main(String[] args) throws IOException {
        new HttpServerHeader().run();
    }

    private void run() throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        connectWifi();
        printLocalIP();

        HttpServer server = HttpServer.create(new InetSocketAddress(80), 4);
        server.createContext("/", HttpServerHeader::handle);
        server.start();
        Serial.println("Listening on port 80");
        while (true) {
            Delay.millis(10000);
        }
    }

    private static void handle(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("GET") || !exchange.getRequestURI().toString().equals("/hello")) {
            send(exchange, HTTP_NOT_FOUND, "{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404}");
            return;
        }
        String clientName = exchange.getRequestHeaders().getFirst("X-Client-Name");
        Serial.print("X-Client-Name length=");
        Serial.println(clientName == null ? -1 : clientName.length());
        if (clientName == null || clientName.length() == 0) {
            send(exchange, HTTP_BAD_REQUEST,
                    "{\"type\":\"about:blank\",\"title\":\"Missing X-Client-Name header\",\"status\":400}");
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        String greeting = buildGreeting(clientName);
        exchange.sendResponseHeaders(HTTP_OK, greeting.length());
        write(exchange, greeting);
    }

    private static String buildGreeting(String clientName) {
        StringBuilder json = new StringBuilder(64);
        json.append("{\"hello\":\"");
        for (int i = 0; i < clientName.length(); i++) {
            char c = clientName.charAt(i);
            if (c != '"' && c != '\\') {
                json.append(c);
            }
        }
        json.append("\"}");
        return json.toString();
    }

    /** An RFC 7807 problem detail. */
    private static void send(HttpExchange exchange, int status, String problem) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/problem+json");
        exchange.sendResponseHeaders(status, problem.length());
        write(exchange, problem);
    }

    private static void write(HttpExchange exchange, String body) throws IOException {
        try (OutputStream output = exchange.getResponseBody()) {
            for (int index = 0; index < body.length(); index++) {
                output.write(body.charAt(index));
            }
        }
    }

    private void connectWifi() {
        Serial.println("Starting WiFi");
        WIFI.begin(MCU.getSecretStore().getString("wifi.ssid"), MCU.getSecretStore().getString("wifi.password"));
        while (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Delay.millis(500);
        }
        Serial.println("WiFi connected");
    }

    private void printLocalIP() {
        int[] ip = new int[4];
        WIFI.localIP(ip);
        Serial.print("IP: ");
        Serial.print(ip[0]);
        Serial.print(".");
        Serial.print(ip[1]);
        Serial.print(".");
        Serial.print(ip[2]);
        Serial.print(".");
        Serial.println(ip[3]);
    }
}
