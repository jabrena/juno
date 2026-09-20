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

    private WeatherClient() {
    }

    /**
     * Fetches the current Madrid forecast into {@code response}.
     *
     * @return the response length, or a non-positive value when the request fails
     */
    public static int fetch(byte[] response, int responseLength) {
        return HttpsClient.get(API_HOST, HTTPS_PORT, API_PATH, response, responseLength);
    }

    /**
     * Returns the current temperature in Celsius as runtime text, exactly as Open-Meteo wrote it
     * (e.g. {@code "21.2"}), or {@code null} when the response does not contain a numeric
     * temperature. Reads the field's raw JSON text directly rather than parsing it through
     * {@link Json#getDouble} and reformatting with {@link String#valueOf(double)}, avoiding that
     * path's binary-floating-point precision loss entirely.
     */
    public static String getTemperature(byte[] response, int responseLength) {
        if (Json.type(response, responseLength, TEMPERATURE_PATH) != Json.TYPE_NUMBER) {
            return null;
        }
        return Json.getString(response, responseLength, TEMPERATURE_PATH);
    }
}
