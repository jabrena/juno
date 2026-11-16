package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.api.hardware.security.ExampleSecrets;
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
import java.time.Duration;

/**
 * The JDK's {@code java.net.http} client over HTTPS, against httpbin.org: a {@code GET} whose query is longer than
 * 255 characters, a {@code POST} with a JSON body and a custom header, and a redirect followed within {@code https}
 * under {@code Redirect.NORMAL}. The client is the resource of a {@code try}-with-resources statement, as in the JDK
 * since 21. Every URI is {@code https}, so only the TLS request is linked, and the server certificate is checked
 * against the board's own root store.
 *
 * <p>Each line prints the request, its status, the body length, the time taken and the response's
 * {@code Content-Type}. The requests are built once, before the loop, which repeats every 30 seconds at 115200 baud.
 * Expected: {@code status=200} for all three; a 302 for {@code REDIRECT} would mean it was not followed.
 */
@Board({
    ArduinoUnoR4WiFiBoard.class,
    ArduinoNanoEsp32Board.class,
    AiThinkerEsp32CamBoard.class,
    M5StackCardputerBoard.class,
    RaspberryPico2WifiBoard.class
})
public final class JdkHttpsRequests {
    private static final Wifi WIFI = MCU.getWifi();
    private static final Clock CLOCK = MCU.getClock();
    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final String LONG_QUERY_URI = "https://httpbin.org/get?select=id,name,price,stock"
            + "&p01=abcdefghij&p02=abcdefghij&p03=abcdefghij&p04=abcdefghij&p05=abcdefghij&p06=abcdefghij"
            + "&p07=abcdefghij&p08=abcdefghij&p09=abcdefghij&p10=abcdefghij&p11=abcdefghij&p12=abcdefghij"
            + "&p13=abcdefghij&p14=abcdefghij&p15=abcdefghij&p16=abcdefghij&p17=abcdefghij&p18=abcdefghij"
            + "&order=id.asc";

    private JdkHttpsRequests() {
    }

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        if (!connectWifi()) {
            return;
        }
        HttpRequest get = HttpRequest.newBuilder(URI.create(LONG_QUERY_URI)).build();
        HttpRequest post = HttpRequest.newBuilder(URI.create("https://httpbin.org/post"))
                .header("X-Juno-Board", "yes")
                .POST(HttpRequest.BodyPublishers.ofString("{\"board\":\"juno\"}"))
                .build();
        HttpRequest redirect = HttpRequest.newBuilder(URI.create("https://httpbin.org/redirect/1")).build();
        try (HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build()) {
            while (true) {
                report("GET", client, get);
                report("POST", client, post);
                report("REDIRECT", client, redirect);
                Serial.println("---");
                Delay.millis(30000);
            }
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
        } catch (IOException e) {
            Serial.println(" failed");
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
