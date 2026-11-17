package io.github.jabrena.juno.api.hardware.net.weather;

import io.github.jabrena.juno.hardware.net.http.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/** Reads Madrid's local time from an Open-Meteo forecast response, with the JDK's {@code java.net.http} client. */
public final class TimeClient {
    private static final String API_URL = "https://api.open-meteo.com/v1/forecast?latitude=40.4168&longitude=-3.7038"
            + "&current=temperature_2m&timezone=Europe%2FMadrid";
    private static final int HOUR_INDEX = 11;
    private static final int MINUTE_INDEX = 14;
    private static final int MINIMUM_TIME_LENGTH = MINUTE_INDEX + 2;
    private static final int HTTP_STATUS_OK = 200;

    private final HttpClient client = HttpClient.newHttpClient();
    private final HttpRequest request = HttpRequest.newBuilder(URI.create(API_URL)).build();

    /**
     * Returns Madrid's current local time formatted as {@code HH:MM}, or {@code null} when the request failed, the
     * status wasn't {@code 200}, or the response didn't contain the fixed-width time field (e.g.
     * {@code "2026-09-20T16:00"}).
     */
    public String fetchTime() throws InterruptedException {
        HttpResponse<String> reply;
        try {
            reply = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            return null;
        }
        if (reply.statusCode() != HTTP_STATUS_OK) {
            return null;
        }
        String time = Json.view(reply.body()).get("current").get("time").asString();
        if (time == null || time.length() < MINIMUM_TIME_LENGTH) {
            return null;
        }
        return formatTime(time);
    }

    /** Formats the ISO-8601 {@code time} (e.g. {@code "2026-09-20T16:00"}) as {@code HH:MM}. */
    private static String formatTime(String time) {
        StringBuilder formatted = new StringBuilder(5);
        formatted.append(time.charAt(HOUR_INDEX));
        formatted.append(time.charAt(HOUR_INDEX + 1));
        formatted.append(':');
        formatted.append(time.charAt(MINUTE_INDEX));
        formatted.append(time.charAt(MINUTE_INDEX + 1));
        return formatted.toString();
    }
}
