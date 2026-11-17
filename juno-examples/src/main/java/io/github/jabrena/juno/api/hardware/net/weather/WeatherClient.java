package io.github.jabrena.juno.api.hardware.net.weather;

import io.github.jabrena.juno.hardware.net.http.Json;
import io.github.jabrena.juno.hardware.net.http.JsonView;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Fetches and reads Madrid's current weather from Open-Meteo, with the JDK's {@code java.net.http} client. */
public final class WeatherClient {
    private static final String API_URL = "https://api.open-meteo.com/v1/forecast?latitude=40.4168&longitude=-3.7038"
            + "&current=temperature_2m&timezone=Europe%2FMadrid";
    private static final int HTTP_STATUS_OK = 200;

    private final HttpClient client = HttpClient.newHttpClient();
    private final HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL)).build();

    /**
     * Returns Madrid's current temperature in Celsius as runtime text, exactly as Open-Meteo wrote it (e.g.
     * {@code "21.2"}), or {@code null} when the request failed, the status wasn't {@code 200}, or the response didn't
     * contain a numeric temperature. Reads the field's raw JSON text rather than parsing it through
     * {@link JsonView#asDouble()} and reformatting it, which would lose precision in binary floating point.
     */
    public String fetchTemperature() throws InterruptedException {
        HttpResponse<String> reply;
        try {
            reply = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            return null;
        }
        String body = reply.body();
        JsonView temperature = Json.view(body).get("current").get("temperature_2m");
        if (reply.statusCode() != HTTP_STATUS_OK || !temperature.isNumber()) {
            return null;
        }
        return temperature.asString();
    }
}
