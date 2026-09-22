package io.github.jabrena.juno.api.io.net.weather;

import io.github.jabrena.juno.api.io.net.http.HttpsClient;
import io.github.jabrena.juno.api.io.net.http.Json;

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
    private static final int HTTP_STATUS_OK = 200;

    private TimeClient() {
    }

    /**
     * Fetches Madrid's current local time into {@code response}/{@code headers} (caller-owned,
     * reused every call), using {@code timeText} as internal scratch space for the raw ISO-8601
     * text (e.g. {@code "2026-09-20T16:00"}), and returns the formatted {@code HH:MM} as a fresh
     * {@link String} — or {@code null} when the request failed, the response status wasn't
     * {@code 200}, or the response didn't contain the fixed-width time field.
     * {@code statusAndHeadersLength}/{@code timeText} are caller-owned scratch space (allocated
     * once outside any loop, like the buffers), since Juno has no heap to allocate them internally.
     */
    public static String fetchTime(byte[] response, int responseLength, byte[] headers, int headersLength,
            int[] statusAndHeadersLength, byte[] timeText, int timeTextLength) {
        int bodyLength = HttpsClient.get(API_HOST, HTTPS_PORT, API_PATH, response, responseLength,
                headers, headersLength, statusAndHeadersLength);
        if (statusAndHeadersLength[0] != HTTP_STATUS_OK || bodyLength <= 0) {
            return null;
        }
        int copiedLength = Json.getString(response, bodyLength, CURRENT_TIME_PATH, timeText, timeTextLength);
        if (copiedLength < MINIMUM_TIME_LENGTH) {
            return null;
        }
        return formatTime(timeText);
    }

    /**
     * Formats the raw ISO-8601 text in {@code timeText} (e.g. {@code "2026-09-20T16:00"}) as
     * {@code HH:MM}.
     */
    private static String formatTime(byte[] timeText) {
        StringBuilder formatted = new StringBuilder(5);
        formatted.append((char) timeText[HOUR_INDEX]);
        formatted.append((char) timeText[HOUR_INDEX + 1]);
        formatted.append(':');
        formatted.append((char) timeText[MINUTE_INDEX]);
        formatted.append((char) timeText[MINUTE_INDEX + 1]);
        return formatted.toString();
    }
}
