package io.github.jabrena.juno.api.io.net.http;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Memory;
import io.github.jabrena.juno.api.io.net.Wifi;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;
import io.github.jabrena.juno.api.lcd.LcdKeypadShield;

/**
 * Demonstrates {@link HttpServer}'s {@code POST} handling: connects to WiFi with build-time
 * credentials from {@code JUNO_WIFI_SSID}/{@code JUNO_WIFI_PASSWORD} (see {@link
 * io.github.jabrena.juno.api.io.net.WifiStatus}), starts the LCD Keypad Shield with its backlight
 * off — {@link LcdKeypadShield#begin} turns it on as part of its own init sequence, so this
 * immediately overrides that — then, on every {@code
 * POST} request regardless of path or body, flashes the backlight on for {@link
 * #BACKLIGHT_ON_MILLIS} and back off before replying. Any other method gets a {@code 405 Method
 * Not Allowed} problem detail instead, printing one line over Serial per request either way so
 * Serial output visibly tracks the HTTP request rate in real time. Every {@link
 * #HEARTBEAT_INTERVAL_MS} also prints a lower-noise summary — request count and {@link
 * Memory#arenaUsedBytes} — for spotting a leak (arena usage that only ever grows) versus healthy
 * behavior (usage that stays flat, since neither response body here is built at runtime — both
 * are compile-time literals).
 */
public final class HttpServerBacklight {
    private static final int BACKLIGHT_ON_MILLIS = 200;
    private static final int HEARTBEAT_INTERVAL_MS = 5000;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        LcdKeypadShield.begin();
        LcdKeypadShield.backlight(false);
        LcdKeypadShield.print("POST to flash");

        connectWifi();
        printLocalIP();

        HttpServer.begin(80);
        Serial.println("Listening on port 80");

        byte[] body = new byte[128];
        int flashCount = 0;
        int requestCount = 0;
        int lastHeartbeat = Clock.millis();
        while (true) {
            int bodyLength = HttpServer.accept(body, body.length);
            if (bodyLength >= 0) {
                String method = HttpServer.method();
                if (method.equals(HttpMethod.POST)) {
                    flashCount = flashCount + 1;
                    flashBacklight();
                    showFlashCount(flashCount);
                    HttpServer.respond(HttpStatus.OK, "application/json", "{\"backlight\":\"flashed\"}");
                    Serial.print("POST flashCount=");
                    Serial.println(flashCount);
                } else {
                    HttpServer.respond(HttpStatus.METHOD_NOT_ALLOWED, "application/problem+json",
                            "{\"type\":\"about:blank\",\"title\":\"Method Not Allowed\",\"status\":405}");
                    Serial.println("Rejected: not POST");
                }
                requestCount = requestCount + 1;
            }
            int now = Clock.millis();
            if (now - lastHeartbeat >= HEARTBEAT_INTERVAL_MS) {
                printHeartbeat(now, requestCount);
                lastHeartbeat = now;
            }
        }
    }

    private static void printHeartbeat(int nowMillis, int requestCount) {
        Serial.print("heartbeat uptime_ms=");
        Serial.print(nowMillis);
        Serial.print(" requests=");
        Serial.print(requestCount);
        Serial.print(" arenaUsedBytes=");
        Serial.println(Memory.arenaUsedBytes());
    }

    private static void flashBacklight() {
        LcdKeypadShield.backlight(true);
        Delay.millis(BACKLIGHT_ON_MILLIS);
        LcdKeypadShield.backlight(false);
    }

    private static void showFlashCount(int flashCount) {
        LcdKeypadShield.setCursor(0, 1);
        LcdKeypadShield.print("Flashes: ");
        LcdKeypadShield.print(flashCount);
    }

    private static void connectWifi() {
        Serial.println("Starting WiFi");
        Wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        while (Wifi.status() != Wifi.STATUS_CONNECTED) {
            Delay.millis(500);
        }
        Serial.println("WiFi connected");
    }

    private static void printLocalIP() {
        int[] ip = new int[4];
        Wifi.localIP(ip);
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
