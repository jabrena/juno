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
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Checks that the board's {@code java.net.http} client validates server certificates, against badssl.com's test hosts:
 * a valid certificate must answer, and an expired, self-signed, wrong-host or untrusted-root one must fail with an
 * {@code IOException}, as on the JDK. Each line prints the host, what it should do and what it did, with {@code OK} or
 * {@code MISMATCH}; the checks repeat every 30 seconds at 115200 baud.
 */
@Board({
    ArduinoUnoR4WiFiBoard.class,
    ArduinoNanoEsp32Board.class,
    AiThinkerEsp32CamBoard.class,
    M5StackCardputerBoard.class,
    RaspberryPico2WifiBoard.class
})
public final class JdkHttpsCertificates {
    private static final Wifi WIFI = MCU.getWifi();
    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    private JdkHttpsCertificates() {
    }

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        if (!connectWifi()) {
            return;
        }
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest valid = HttpRequest.newBuilder(URI.create("https://badssl.com/")).build();
        HttpRequest expired = HttpRequest.newBuilder(URI.create("https://expired.badssl.com/")).build();
        HttpRequest selfSigned = HttpRequest.newBuilder(URI.create("https://self-signed.badssl.com/")).build();
        HttpRequest wrongHost = HttpRequest.newBuilder(URI.create("https://wrong.host.badssl.com/")).build();
        HttpRequest untrustedRoot = HttpRequest.newBuilder(URI.create("https://untrusted-root.badssl.com/")).build();
        while (true) {
            check("valid", client, valid, true);
            check("expired", client, expired, false);
            check("self-signed", client, selfSigned, false);
            check("wrong-host", client, wrongHost, false);
            check("untrusted-root", client, untrustedRoot, false);
            Serial.println("---");
            Delay.millis(30000);
        }
    }

    private static void check(String name, HttpClient client, HttpRequest request, boolean shouldAnswer)
            throws InterruptedException {
        boolean answered;
        int status = 0;
        try {
            status = client.send(request, HttpResponse.BodyHandlers.ofByteArray()).statusCode();
            answered = status > 0;
        } catch (IOException e) {
            answered = false;
        }
        Serial.print(name);
        Serial.print(shouldAnswer ? " expect=answer" : " expect=reject");
        Serial.print(answered ? " got=answer status=" : " got=reject status=");
        Serial.print(status);
        Serial.println(answered == shouldAnswer ? " OK" : " MISMATCH");
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
