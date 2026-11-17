package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.Json;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * GET, POST, DELETE, PATCH and QUERY over HTTPS with the JDK's {@code java.net.http} client, against echo services
 * that answer with the method they received: each line prints {@code OK} when the echoed {@code method} matches.
 * {@code QUERY} (RFC 10008) is sent with {@code HttpRequest.Builder.method}, as any other method.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoPortentaH7Board.class})
public final class HttpsMethods {
    private static final Wifi WIFI = MCU.getWifi();

    private static final URI HTTPBIN = URI.create("https://httpbin.org/anything");
    private static final URI HTTPBUN = URI.create("https://httpbun.com/anything");

    private HttpsMethods() {
    }

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        WIFI.begin(MCU.getSecretStore().getString("wifi.ssid"), MCU.getSecretStore().getString("wifi.password"));
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < 30) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("WiFi connection failed");
            return;
        }
        // The Wi-Fi reports connected before its DHCP/DNS is ready: on the UNO R4 WiFi the first request right after it
        // fails, so the network gets a moment to settle first.
        Delay.millis(2000);

        HttpClient client = HttpClient.newHttpClient();
        check(client, "GET", HttpRequest.newBuilder(HTTPBIN).GET().build());
        check(client, "POST", HttpRequest.newBuilder(HTTPBIN).POST(json("POST")).build());
        check(client, "DELETE", HttpRequest.newBuilder(HTTPBIN).DELETE().build());
        check(client, "PATCH", HttpRequest.newBuilder(HTTPBIN).method("PATCH", json("PATCH")).build());
        check(client, "QUERY", HttpRequest.newBuilder(HTTPBUN).method("QUERY", json("QUERY")).build());
    }

    private static HttpRequest.BodyPublisher json(String verb) {
        return HttpRequest.BodyPublishers.ofString("{\"verb\":\"" + verb + "\"}");
    }

    /** Sends {@code request} and prints whether the service echoed {@code method}. */
    private static void check(HttpClient client, String method, HttpRequest request) throws InterruptedException {
        Serial.print("HTTPS ");
        Serial.print(method);
        Serial.print(": ");
        try {
            String echoed = Json.getString(client.send(request, HttpResponse.BodyHandlers.ofString()).body(), "method");
            Serial.println(method.equals(echoed) ? "OK" : "FAILED");
        } catch (IOException e) {
            Serial.println("FAILED");
        }
        Delay.millis(500);
    }
}
