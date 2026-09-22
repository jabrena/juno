package io.github.jabrena.juno.api.io.net.http;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Memory;
import io.github.jabrena.juno.api.io.net.Wifi;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/**
 * A minimal REST endpoint for the experimental {@code CortexM4AsmBackend}: connects to WiFi with
 * build-time credentials from {@code JUNO_WIFI_SSID}/{@code JUNO_WIFI_PASSWORD} (see {@link
 * io.github.jabrena.juno.api.io.net.WifiStatus}), prints its {@link Wifi#localIP} once connected
 * so the endpoint can actually be found on the network, then serves {@code GET /status} as JSON
 * ({@code
 * {"uptime_ms":<Clock.millis()>}}) on port 80, and replies to everything else with an RFC 7807
 * problem detail ({@code application/problem+json}). Demonstrates {@link HttpServer}'s
 * non-blocking {@code accept()}/{@code respond()} pair, {@link String#equals} route matching, and
 * building a runtime JSON body in a {@link StringBuilder} (a literal part appended whole, a
 * computed number or the request path appended char-by-char via {@link String#charAt}, since a
 * {@code StringBuilder}'s {@code append(String)} only accepts a compile-time literal) — returned
 * either as a plain {@code String} ({@link #buildStatusResponse}, short enough for {@code
 * toString()}'s bounded pool) or as the {@code StringBuilder} itself ({@link
 * #buildNotFoundProblem}, long enough that it can't be). Every {@link #HEARTBEAT_INTERVAL_MS}
 * also prints request count and {@link Memory#arenaUsedBytes} over Serial, for spotting a leak
 * (arena usage that only ever grows) versus healthy reclamation (usage that rises and falls).
 */
public final class HttpServerStatus {
    /** How often to print an arena-usage heartbeat over Serial, in milliseconds. */
    private static final int HEARTBEAT_INTERVAL_MS = 5000;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        connectWifi();
        printLocalIP();

        HttpServer.begin(80);
        Serial.println("Listening on port 80");

        byte[] body = new byte[128];
        int requestCount = 0;
        int lastHeartbeat = Clock.millis();
        while (true) {
            int bodyLength = HttpServer.accept(body, body.length);
            if (bodyLength >= 0) {
                String method = HttpServer.method();
                String path = HttpServer.path();
                Serial.print("Request path length=");
                Serial.println(path.length());
                getStatusHandler(method, path);
                requestCount = requestCount + 1;
            }
            int now = Clock.millis();
            if (now - lastHeartbeat >= HEARTBEAT_INTERVAL_MS) {
                printHeartbeat(now, requestCount);
                lastHeartbeat = now;
            }
        }
    }

    /**
     * A value that keeps climbing and never comes back down across many heartbeats, while
     * requests keep arriving, would point at a leak (something holding arena references it
     * shouldn't); one that rises and falls as requests are served and reclaimed by the collector
     * is healthy. See {@link Memory#arenaUsedBytes}.
     */
    private static void printHeartbeat(int nowMillis, int requestCount) {
        Serial.print("heartbeat uptime_ms=");
        Serial.print(nowMillis);
        Serial.print(" requests=");
        Serial.print(requestCount);
        Serial.print(" arenaUsedBytes=");
        Serial.println(Memory.arenaUsedBytes());
    }

    private static void getStatusHandler(String method, String path) {
        if (method.equals(HttpMethod.GET) && path.equals("/status")) {
            HttpServer.respond(HttpStatus.OK, "application/json", buildStatusResponse());
        } else {
            HttpServer.respond(HttpStatus.NOT_FOUND, "application/problem+json", buildNotFoundProblem(path));
        }
    }

    private static String buildStatusResponse() {
        StringBuilder json = new StringBuilder(48);
        json.append("{\"uptime_ms\":");
        String uptime = String.valueOf(Clock.millis());
        for (int i = 0; i < uptime.length(); i++) {
            json.append(uptime.charAt(i));
        }
        json.append('}');
        return json.toString();
    }

    /**
     * An RFC 7807 problem detail ({@code application/problem+json}). Its {@code instance} echoes
     * the unmatched request path, so the full body can run well past 32 bytes — too long for
     * {@link #buildStatusResponse}'s {@code String}-returning, {@code toString()}-based approach,
     * which panics instead of truncating past that pool size. Returning the {@link StringBuilder}
     * itself for {@link HttpServer#respond(int, String, StringBuilder)} to stream has no such cap.
     */
    private static StringBuilder buildNotFoundProblem(String path) {
        StringBuilder problem = new StringBuilder(128);
        problem.append("{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404,\"instance\":\"");
        for (int i = 0; i < path.length(); i++) {
            problem.append(path.charAt(i));
        }
        problem.append("\"}");
        return problem;
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
