package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;

/**
 * Every request feature of the JDK's {@code java.net.http} client on the board, against httpbin.org over plain HTTP:
 * a {@code POST} with a JSON body and custom headers, {@code PUT}, {@code PATCH}, {@code DELETE}, {@code HEAD}, a
 * two-hop redirect followed under {@code Redirect.NORMAL}, a request timeout on a slow endpoint, and a
 * {@code CONNECT}. Each line prints the method, the status, the body length, the time taken and the response's
 * {@code Content-Type}; the requests are built once, before the loop, and repeat every 30 seconds at 115200 baud.
 *
 * <p>Expected: 200 for the five methods and the redirect (a 302 would mean it was not followed), {@code HEAD} as
 * quick as {@code GET}-sized requests (no waiting for a body that never comes), {@code timeout} after about 2 seconds,
 * and whatever status httpbin.org gives a {@code CONNECT}. All URIs are {@code http}, so no TLS client is linked.
 */
@Board({
    ArduinoUnoR4WiFiBoard.class,
    ArduinoNanoEsp32Board.class,
    AiThinkerEsp32CamBoard.class,
    M5StackCardputerBoard.class,
    RaspberryPico2WifiBoard.class,
    ArduinoPortentaH7Board.class
})
public final class JdkHttpRequests {
    private static final Wifi WIFI = MCU.getWifi();
    private static final Clock CLOCK = MCU.getClock();
    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final String JSON = "{\"board\":\"juno\"}";

    private JdkHttpRequests() {
    }

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        if (!connectWifi()) {
            return;
        }
        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest post = HttpRequest.newBuilder(URI.create("http://httpbin.org/post"))
                .header("Accept", "application/json")
                .header("X-Juno-Board", "yes")
                .POST(HttpRequest.BodyPublishers.ofString(JSON))
                .build();
        HttpRequest put = HttpRequest.newBuilder(URI.create("http://httpbin.org/put"))
                .PUT(HttpRequest.BodyPublishers.ofString(JSON)).build();
        HttpRequest patch = HttpRequest.newBuilder(URI.create("http://httpbin.org/patch"))
                .method("PATCH", HttpRequest.BodyPublishers.ofString(JSON)).build();
        HttpRequest delete = HttpRequest.newBuilder(URI.create("http://httpbin.org/delete")).DELETE().build();
        HttpRequest head = HttpRequest.newBuilder(URI.create("http://httpbin.org/get")).HEAD().build();
        HttpRequest redirect = HttpRequest.newBuilder(URI.create("http://httpbin.org/redirect/2")).build();
        HttpRequest slow = HttpRequest.newBuilder(URI.create("http://httpbin.org/delay/5"))
                .timeout(Duration.ofSeconds(2)).build();
        HttpRequest connect = HttpRequest.newBuilder(URI.create("http://httpbin.org/"))
                .method("CONNECT", HttpRequest.BodyPublishers.noBody()).build();
        while (true) {
            report("POST", client, post);
            report("PUT", client, put);
            report("PATCH", client, patch);
            report("DELETE", client, delete);
            report("HEAD", client, head);
            report("REDIRECT", client, redirect);
            report("TIMEOUT", client, slow);
            report("CONNECT", client, connect);
            Serial.println("---");
            Delay.millis(30000);
        }
    }

    private static void report(String name, HttpClient client, HttpRequest request) throws InterruptedException {
        int start = CLOCK.millis();
        Serial.print(name);
        try {
            HttpResponse<String> reply = client.send(request, HttpResponse.BodyHandlers.ofString());
            Serial.print(" status=");
            Serial.print(reply.statusCode());
            Serial.print(" length=");
            Serial.print(reply.body().length());
            Serial.print(" ms=");
            Serial.print(CLOCK.millis() - start);
            Serial.print(" type=");
            Serial.println(reply.headers().firstValue("content-type").orElse("-"));
        } catch (HttpTimeoutException e) {
            Serial.print(" timeout ms=");
            Serial.println(CLOCK.millis() - start);
        } catch (IOException e) {
            Serial.println(" failed");
        }
    }

    private static boolean connectWifi() {
        WIFI.begin(MCU.getSecretStore().getString("wifi.ssid"), MCU.getSecretStore().getString("wifi.password"));
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (WIFI.status() == Wifi.STATUS_CONNECTED) {
            Serial.println("wifi connected");
            return true;
        }
        Serial.println("wifi connection failed");
        return false;
    }
}
