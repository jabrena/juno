package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
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
 * One HTTPS request with the JDK's {@code java.net.http} client, used as the serial baseline for
 * {@link ParallelHttpsGet}. Only the status matters, so the body is read with {@code ofByteArray()}, whose fixed
 * buffer keeps the first bytes of a page of any size.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoPortentaH7Board.class})
public final class SingleHttpsGet {
    private static final Wifi WIFI = MCU.getWifi();

    private static final String URL = "https://example.com/?request=single";

    public static void main(String[] args) throws InterruptedException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        if (!connectWifi()) {
            return;
        }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(URL)).build();
        try {
            HttpResponse<byte[]> reply = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            Serial.print("single status=");
            Serial.println(reply.statusCode());
        } catch (IOException e) {
            Serial.println("single failed");
        }
    }

    private static boolean connectWifi() {
        WIFI.begin(MCU.getSecretStore().getString("wifi.ssid"), MCU.getSecretStore().getString("wifi.password"));
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < 30) {
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
