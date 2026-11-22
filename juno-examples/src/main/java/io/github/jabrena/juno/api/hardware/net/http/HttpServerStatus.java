package io.github.jabrena.juno.api.hardware.net.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.jabrena.juno.hardware.BoardMemory;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * A minimal REST endpoint on the JDK {@code HttpServer}: connects to WiFi with credentials from the board's secret
 * store (see {@link io.github.jabrena.juno.api.hardware.net.wifi.WifiStatus}), prints its {@link Wifi#localIP} once
 * connected so the endpoint can actually be found on the network, then serves {@code GET /status} as JSON
 * ({@code {"uptime_ms":<clock.millis()>}}) on port 80, and replies to everything else with an RFC 7807 problem detail
 * ({@code application/problem+json}) whose {@code instance} echoes the request path. Both bodies are built at runtime in
 * a {@link StringBuilder}. Every {@link #HEARTBEAT_INTERVAL_MS} the main thread prints the request count and
 * {@link BoardMemory#arenaUsedBytes} over Serial, for spotting a leak (arena usage that only ever grows) versus
 * healthy reclamation (usage that rises and falls).
 */
public final class HttpServerStatus {
    private static final Wifi WIFI = MCU.getWifi();

    private static final BoardMemory MEMORY = MCU.getBoardMemory();
    private final Clock clock = MCU.getClock();
    /** How often to print an arena-usage heartbeat over Serial, in milliseconds. */
    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    private int requestCount;

    public static void main(String[] args) throws IOException {
        new HttpServerStatus().run();
    }

    private void run() throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        connectWifi();
        printLocalIP();

        HttpServer server = HttpServer.create(new InetSocketAddress(80), 4);
        server.createContext("/status", this::status);
        server.createContext("/", this::notFound);
        server.start();
        Serial.println("Listening on port 80");

        while (true) {
            Delay.millis(HEARTBEAT_INTERVAL_MS);
            printHeartbeat(clock.millis());
        }
    }

    /**
     * A value that keeps climbing and never comes back down across many heartbeats, while requests keep arriving,
     * would point at a leak (something holding arena references it shouldn't); one that rises and falls as requests
     * are served and reclaimed by the collector is healthy. See {@link BoardMemory#arenaUsedBytes}.
     */
    private void printHeartbeat(int nowMillis) {
        Serial.print("heartbeat uptime_ms=");
        Serial.print(nowMillis);
        Serial.print(" requests=");
        Serial.print(requestCount);
        Serial.print(" arenaUsedBytes=");
        Serial.println(MEMORY.arenaUsedBytes());
    }

    private void status(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().toString();
        if (!exchange.getRequestMethod().equals("GET") || !path.equals("/status")) {
            notFound(exchange);
            return;
        }
        logRequest(path);
        StringBuilder json = new StringBuilder(48);
        json.append("{\"uptime_ms\":");
        json.append(String.valueOf(clock.millis()));
        json.append('}');
        send(exchange, HTTP_OK, "application/json", json.toString());
    }

    /** An RFC 7807 problem detail ({@code application/problem+json}) whose {@code instance} echoes the path. */
    private void notFound(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().toString();
        logRequest(path);
        StringBuilder problem = new StringBuilder(128);
        problem.append("{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404,\"instance\":\"");
        problem.append(path);
        problem.append("\"}");
        send(exchange, HTTP_NOT_FOUND, "application/problem+json", problem.toString());
    }

    private void logRequest(String path) {
        Serial.print("Request path length=");
        Serial.println(path.length());
        requestCount = requestCount + 1;
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
