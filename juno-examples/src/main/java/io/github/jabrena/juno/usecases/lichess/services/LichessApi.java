package io.github.jabrena.juno.usecases.lichess.services;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.usecases.lichess.challenge.LichessModel;
import io.github.jabrena.juno.usecases.lichess.tv.TvModel;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;

/**
 * The network model service for the public Lichess puzzle endpoints, with the JDK's {@code java.net.http} client.
 * Each response body is a {@code String} sized to it; the TV feed never ends, so its request times out after
 * {@value #TV_HTTP_TIMEOUT} ms and keeps the lines received by then.
 */
public final class LichessApi {
    private static final int WIFI_ATTEMPTS = 30;
    private static final int TV_HTTP_TIMEOUT = 1500;

    private final Wifi wifi = MCU.getWifi();
    private final HttpClient client = HttpClient.newHttpClient();
    private final HttpRequest daily = HttpRequest.newBuilder(URI.create("https://lichess.org/api/puzzle/daily")).build();
    private final HttpRequest fresh = HttpRequest.newBuilder(URI.create("https://lichess.org/api/puzzle/next")).build();
    private final HttpRequest tv = HttpRequest.newBuilder(URI.create("https://lichess.org/api/tv/feed"))
            .timeout(Duration.ofMillis(TV_HTTP_TIMEOUT)).build();

    /** Reads wifi.ssid and wifi.password from the TFT shield's application.properties and joins that network. */
    public boolean connect(SdCard sdCard) {
        try (InputStream file = sdCard.open("application.properties")) {
            if (file == null) {
                Serial.println("Lichess: application.properties not found");
                return false;
            }
            Properties properties = new Properties();
            properties.load(file);
            String ssid = properties.getProperty("wifi.ssid");
            String password = properties.getProperty("wifi.password");
            if (ssid == null || password == null) {
                Serial.println("Lichess: WiFi settings are missing");
                return false;
            }
            wifi.begin(ssid, password);
        } catch (IOException failed) {
            Serial.println("Lichess: could not read application.properties");
            return false;
        }
        for (int attempt = 0; wifi.status() != Wifi.STATUS_CONNECTED && attempt < WIFI_ATTEMPTS; attempt++) {
            Delay.millis(1000);
        }
        if (wifi.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("Lichess: WiFi connection failed");
            return false;
        }
        return true;
    }

    /** Downloads the current daily puzzle. */
    public boolean loadDaily(LichessModel model) {
        HttpResponse<String> reply = send(daily);
        return model.loadResponse(reply == null ? "" : reply.body(), reply == null ? 0 : reply.statusCode(),
                LichessModel.DAILY);
    }

    /** Downloads a public fresh puzzle, useful when the player wants another challenge. */
    public boolean loadFresh(LichessModel model) {
        HttpResponse<String> reply = send(fresh);
        return model.loadResponse(reply == null ? "" : reply.body(), reply == null ? 0 : reply.statusCode(),
                LichessModel.FRESH);
    }

    /** Reads the current game and recent positions from the public Lichess TV stream. */
    public boolean loadTv(TvModel model) {
        HttpResponse<String> reply = send(tv);
        return reply != null && model.loadResponse(reply.body(), reply.statusCode());
    }

    /** The reply to {@code request}, or {@code null} when it could not be sent. */
    private HttpResponse<String> send(HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            // The board never interrupts a request; either way there is no reply to show.
            return null;
        }
    }
}
