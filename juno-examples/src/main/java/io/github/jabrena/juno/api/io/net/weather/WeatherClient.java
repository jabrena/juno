package io.github.jabrena.juno.api.io.net.weather;

import io.github.jabrena.juno.api.io.net.HttpsClient;
import io.github.jabrena.juno.api.io.net.Json;

/** Fetches and reads Madrid's current weather from Open-Meteo. */
public final class WeatherClient {
    private static final String API_HOST = "api.open-meteo.com";
    private static final int HTTPS_PORT = 443;
    private static final String API_PATH = "/v1/forecast?latitude=40.4168&longitude=-3.7038"
            + "&current=temperature_2m&timezone=Europe%2FMadrid";
    private static final String TEMPERATURE_PATH = "current.temperature_2m";
    private static final int HTTP_STATUS_OK = 200;

    private WeatherClient() {
    }

    /**
     * Fetches the current Madrid forecast into {@code response}/{@code headers} (caller-owned,
     * reused every call) and returns the current temperature in Celsius as runtime text, exactly
     * as Open-Meteo wrote it (e.g. {@code "21.2"}) — or {@code null} when the request failed, the
     * response status wasn't {@code 200}, or the response didn't contain a numeric temperature.
     * Reads the field's raw JSON text directly rather than parsing it through {@link
     * Json#getDouble} and reformatting with {@link String#valueOf(double)}, avoiding that path's
     * binary-floating-point precision loss entirely. {@code statusAndHeadersLength} is
     * caller-owned scratch space (allocated once outside any loop, like the buffers), since Juno
     * has no heap to allocate it internally — for the same reason, this reads the status/body
     * directly off {@code statusAndHeadersLength}/the returned body length rather than wrapping
     * them in an {@link io.github.jabrena.juno.api.io.net.HttpResponse}, since a loop calling this
     * forever would otherwise allocate one every iteration.
     */
    public static String fetchTemperature(byte[] response, int responseLength, byte[] headers, int headersLength,
            int[] statusAndHeadersLength) {
        int bodyLength = HttpsClient.get(API_HOST, HTTPS_PORT, API_PATH, response, responseLength,
                headers, headersLength, statusAndHeadersLength);
        if (statusAndHeadersLength[0] != HTTP_STATUS_OK || bodyLength <= 0) {
            return null;
        }
        if (Json.type(response, bodyLength, TEMPERATURE_PATH) != Json.TYPE_NUMBER) {
            return null;
        }
        return Json.getString(response, bodyLength, TEMPERATURE_PATH);
    }
}
