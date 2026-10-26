package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpMethod;
import io.github.jabrena.juno.hardware.net.http.HttpServer;
import io.github.jabrena.juno.hardware.net.http.HttpStatus;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Reads a request header with {@link HttpServer#header}: connects to WiFi with build-time credentials from
 * {@code JUNO_WIFI_SSID}/{@code JUNO_WIFI_PASSWORD}, prints its {@link Wifi#localIP}, then serves {@code GET /hello}
 * on port 80. The reply greets the caller by the {@code X-Client-Name} header ({@code {"hello":"<name>"}}); a request
 * without it gets a {@code 400} RFC 7807 problem detail, and any other route a {@code 404}. Try it with
 * {@code curl -H "X-Client-Name: Ada" http://<ip>/hello}.
 *
 * <p>The header value lands in a caller-owned {@code byte[]} allocated once at startup, so the loop allocates nothing
 * to read it. Quotes and backslashes are dropped from the echoed value to keep the JSON body valid.
 */
public final class HttpServerHeader {
    private static final Wifi WIFI = ArduinoUno.getWifi();

    public static void main(String[] args) {
        new HttpServerHeader().run();
    }

    private void run() {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        connectWifi();
        printLocalIP();

        HttpServer.begin(80);
        Serial.println("Listening on port 80");

        byte[] body = new byte[64];
        byte[] clientName = new byte[24];
        while (true) {
            if (HttpServer.accept(body, body.length) >= 0) {
                handle(HttpServer.method(), HttpServer.path(), clientName);
            }
        }
    }

    private void handle(String method, String path, byte[] clientName) {
        if (!method.equals(HttpMethod.GET) || !path.equals("/hello")) {
            HttpServer.respond(HttpStatus.NOT_FOUND, "application/problem+json",
                    "{\"type\":\"about:blank\",\"title\":\"Not Found\",\"status\":404}");
            return;
        }
        int length = HttpServer.header("X-Client-Name", clientName, clientName.length);
        Serial.print("X-Client-Name length=");
        Serial.println(length);
        if (length <= 0) {
            HttpServer.respond(HttpStatus.BAD_REQUEST, "application/problem+json",
                    "{\"type\":\"about:blank\",\"title\":\"Missing X-Client-Name header\",\"status\":400}");
            return;
        }
        HttpServer.respond(HttpStatus.OK, "application/json", buildGreeting(clientName, length));
    }

    private StringBuilder buildGreeting(byte[] clientName, int length) {
        StringBuilder json = new StringBuilder(64);
        json.append("{\"hello\":\"");
        for (int i = 0; i < length; i++) {
            char c = (char) clientName[i];
            if (c != '"' && c != '\\') {
                json.append(c);
            }
        }
        json.append("\"}");
        return json;
    }

    private void connectWifi() {
        Serial.println("Starting WiFi");
        WIFI.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
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
