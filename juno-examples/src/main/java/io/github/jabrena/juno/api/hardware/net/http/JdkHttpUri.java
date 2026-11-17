package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.api.hardware.security.ExampleSecrets;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * The JDK's {@code java.net.URI} and an {@code AutoCloseable} {@code java.net.http} client on the board, against
 * httpbin.org over plain HTTP:
 *
 * <ul>
 *   <li>a {@code GET} whose query is longer than 255 characters, which the board-side {@code URI} used to reject;</li>
 *   <li>a reference resolved against it, {@code ../v2/items?page=2#top}, read back through {@code getHost},
 *       {@code getPath}, {@code getQuery} and {@code getFragment}, compared with {@code equals} to the same URI written
 *       with an upper-case scheme and host, and then sent;</li>
 *   <li>a client used as the resource of a {@code try}-with-resources statement, which is terminated after it and
 *       refuses to send.</li>
 * </ul>
 *
 * <p>The URIs, requests and client are created once, before the loop, which repeats every 30 seconds at 115200 baud.
 * Expected: {@code long status=200}, {@code resolved status=200}, {@code equal=true}, {@code closed=true} and
 * {@code refused=true}.
 */
@Board({
    ArduinoUnoR4WiFiBoard.class,
    ArduinoNanoEsp32Board.class,
    AiThinkerEsp32CamBoard.class,
    M5StackCardputerBoard.class,
    RaspberryPico2WifiBoard.class,
    ArduinoPortentaH7Board.class
})
public final class JdkHttpUri {
    private static final Wifi WIFI = MCU.getWifi();
    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final String LONG_URI = "http://httpbin.org/anything/juno/v1/items?select=id,name,price,stock"
            + "&p01=abcdefghij&p02=abcdefghij&p03=abcdefghij&p04=abcdefghij&p05=abcdefghij&p06=abcdefghij"
            + "&p07=abcdefghij&p08=abcdefghij&p09=abcdefghij&p10=abcdefghij&p11=abcdefghij&p12=abcdefghij"
            + "&p13=abcdefghij&p14=abcdefghij&p15=abcdefghij&p16=abcdefghij&p17=abcdefghij&p18=abcdefghij"
            + "&p19=abcdefghij&p20=abcdefghij&p21=abcdefghij&p22=abcdefghij&p23=abcdefghij&p24=abcdefghij"
            + "&p25=abcdefghij&p26=abcdefghij&p27=abcdefghij&p28=abcdefghij&order=id.asc";

    private JdkHttpUri() {
    }

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        if (!connectWifi()) {
            return;
        }
        URI longUri = URI.create(LONG_URI);
        URI resolved = longUri.resolve("../v2/items?page=2#top");
        boolean equal = resolved.equals(URI.create("HTTP://HTTPBIN.org/anything/juno/v2/items?page=2#top"));
        HttpRequest longRequest = HttpRequest.newBuilder(longUri).build();
        HttpRequest resolvedRequest = HttpRequest.newBuilder(resolved).build();

        HttpClient closedClient;
        try (HttpClient client = HttpClient.newHttpClient()) {
            closedClient = client;
        }
        boolean closed = closedClient.isTerminated();
        boolean refused = refuses(closedClient, longRequest);

        HttpClient client = HttpClient.newHttpClient();
        while (true) {
            Serial.print("uri length=");
            Serial.println(LONG_URI.length());
            report("long", client, longRequest);
            Serial.print("resolved host=");
            Serial.print(orDash(resolved.getHost()));
            Serial.print(" path=");
            Serial.print(orDash(resolved.getPath()));
            Serial.print(" query=");
            Serial.print(orDash(resolved.getQuery()));
            Serial.print(" fragment=");
            Serial.println(orDash(resolved.getFragment()));
            report("resolved", client, resolvedRequest);
            Serial.print("equal=");
            Serial.print(equal ? "true" : "false");
            Serial.print(" closed=");
            Serial.print(closed ? "true" : "false");
            Serial.print(" refused=");
            Serial.println(refused ? "true" : "false");
            Serial.println("---");
            Delay.millis(30000);
        }
    }

    private static void report(String name, HttpClient client, HttpRequest request) throws InterruptedException {
        Serial.print(name);
        try {
            HttpResponse<byte[]> reply = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            Serial.print(" status=");
            Serial.println(reply.statusCode());
        } catch (IOException e) {
            Serial.println(" failed");
        }
    }

    private static String orDash(String text) {
        return text == null ? "-" : text;
    }

    /** Whether {@code client} refuses to send {@code request}, as a shut-down client does. */
    private static boolean refuses(HttpClient client, HttpRequest request) throws InterruptedException {
        try {
            client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    private static boolean connectWifi() {
        WIFI.begin(ExampleSecrets.wifiSsid(), ExampleSecrets.wifiPassword());
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
