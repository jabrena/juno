package io.github.jabrena.juno.api.hardware.net.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.jabrena.juno.hardware.BoardMemory;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.lcd.LcdKeypadShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static java.net.HttpURLConnection.HTTP_BAD_METHOD;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * Handles {@code POST} with the JDK {@code HttpServer}: connects to WiFi with credentials from the board's secret
 * store (see {@link io.github.jabrena.juno.api.hardware.net.wifi.WifiStatus}), starts the LCD Keypad Shield with its
 * backlight off ({@link LcdKeypadShield#begin} turns it on as part of its own init sequence, so this immediately
 * overrides that), then, on every {@code POST} request regardless of path or body, flashes the backlight on for
 * {@link #BACKLIGHT_ON_MILLIS} and back off before replying. Any other method gets a {@code 405 Method Not Allowed}
 * problem detail instead, printing one line over Serial per request either way so Serial output visibly tracks the
 * HTTP request rate in real time. Every {@link #HEARTBEAT_INTERVAL_MS} the main thread also prints a lower-noise
 * summary (request count and {@link BoardMemory#arenaUsedBytes}) for spotting a leak (arena usage that only ever
 * grows) versus healthy behavior (usage that stays flat, since both response bodies are compile-time literals).
 */
public final class HttpServerBacklight {
    private static final Wifi WIFI = MCU.getWifi();

    private static final BoardMemory MEMORY = MCU.getBoardMemory();
    private final LcdKeypadShield lcd = new LcdKeypadShield();
    private final Clock clock = MCU.getClock();
    private static final int BACKLIGHT_ON_MILLIS = 200;
    private static final int HEARTBEAT_INTERVAL_MS = 5000;
    private int flashCount;
    private int requestCount;

    public static void main(String[] args) throws IOException {
        new HttpServerBacklight().run();
    }

    private void run() throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        lcd.backlight(false);
        lcd.print("POST to flash");

        connectWifi();
        printLocalIP();

        HttpServer server = HttpServer.create(new InetSocketAddress(80), 4);
        server.createContext("/", this::handle);
        server.start();
        Serial.println("Listening on port 80");

        while (true) {
            Delay.millis(HEARTBEAT_INTERVAL_MS);
            printHeartbeat(clock.millis(), requestCount);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        requestCount = requestCount + 1;
        if (exchange.getRequestMethod().equals("POST")) {
            flashCount = flashCount + 1;
            flashBacklight();
            showFlashCount(flashCount);
            send(exchange, HTTP_OK, "application/json", "{\"backlight\":\"flashed\"}");
            Serial.print("POST flashCount=");
            Serial.println(flashCount);
        } else {
            send(exchange, HTTP_BAD_METHOD, "application/problem+json",
                    "{\"type\":\"about:blank\",\"title\":\"Method Not Allowed\",\"status\":405}");
            Serial.println("Rejected: not POST");
        }
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

    private void printHeartbeat(int nowMillis, int requestCount) {
        Serial.print("heartbeat uptime_ms=");
        Serial.print(nowMillis);
        Serial.print(" requests=");
        Serial.print(requestCount);
        Serial.print(" arenaUsedBytes=");
        Serial.println(MEMORY.arenaUsedBytes());
    }

    private void flashBacklight() {
        lcd.backlight(true);
        Delay.millis(BACKLIGHT_ON_MILLIS);
        lcd.backlight(false);
    }

    private void showFlashCount(int flashCount) {
        lcd.setCursor(0, 1);
        lcd.print("Flashes: ");
        lcd.print(flashCount);
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
