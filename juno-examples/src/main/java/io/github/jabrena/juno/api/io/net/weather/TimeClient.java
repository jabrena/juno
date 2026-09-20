package io.github.jabrena.juno.api.io.net.weather;

import io.github.jabrena.juno.api.io.net.HttpsClient;
import io.github.jabrena.juno.api.io.net.Json;

/** Reads Madrid's local time from an Open-Meteo forecast response. */
public final class TimeClient {
    private static final String API_HOST = "api.open-meteo.com";
    private static final int HTTPS_PORT = 443;
    private static final String API_PATH = "/v1/forecast?latitude=40.4168&longitude=-3.7038"
            + "&current=temperature_2m&timezone=Europe%2FMadrid";
    private static final String CURRENT_TIME_PATH = "current.time";
    private static final int HOUR_INDEX = 11;
    private static final int MINUTE_INDEX = 14;
    private static final int MINIMUM_TIME_LENGTH = MINUTE_INDEX + 2;

    private TimeClient() {
    }

    /**
     * Fetches Madrid's current local time into {@code response}.
     *
     * @return the response length, or a non-positive value when the request fails
     */
    public static int fetch(byte[] response, int responseLength) {
        return HttpsClient.get(API_HOST, HTTPS_PORT, API_PATH, response, responseLength);
    }

    /**
     * Copies the current ISO-8601 local time into {@code timeText}.
     *
     * @return the copied time length, or a non-positive value when the field cannot be read
     */
    public static int read(byte[] response, int responseLength, byte[] timeText, int timeTextLength) {
        return Json.getString(response, responseLength, CURRENT_TIME_PATH, timeText, timeTextLength);
    }

    /** Returns whether the copied value contains the fixed-width {@code HH:MM} positions. */
    public static boolean isAvailable(int timeTextLength) {
        return timeTextLength >= MINIMUM_TIME_LENGTH;
    }

    /** Appends {@code HH:MM} to {@code message} and returns the next free index. */
    public static int appendTime(byte[] timeText, int[] message, int index) {
        message[index] = timeText[HOUR_INDEX];
        message[index + 1] = timeText[HOUR_INDEX + 1];
        message[index + 2] = ':';
        message[index + 3] = timeText[MINUTE_INDEX];
        message[index + 4] = timeText[MINUTE_INDEX + 1];
        return index + 5;
    }
}
