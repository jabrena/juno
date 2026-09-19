import io.github.jabrena.juno.api.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.api.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.led.LedCanvas;
import io.github.jabrena.juno.api.led.LedMatrix;
import io.github.jabrena.juno.api.net.HttpClient;
import io.github.jabrena.juno.api.net.Json;
import io.github.jabrena.juno.api.net.Wifi;

/**
 * Connects to WiFi, then periodically GETs Madrid's current local time from the free, no-API-key
 * WorldClockAPI (Central European Time, Madrid's zone) and current temperature from the free,
 * no-API-key Open-Meteo forecast API — both plain HTTP, not HTTPS, since {@link HttpClient} has no
 * TLS support — and scrolls "Madrid Time: HH:MM" followed by "Madrid Weather: &lt;temp&gt;C" across
 * the UNO R4 WiFi's 12x8 LED matrix, one message per HTTP response. Uses the same right-to-left
 * one-pixel-per-tick idea as {@code LedMatrixScrollingText}, adapted for messages built at runtime
 * instead of a compile-time literal. Credentials come from {@code JUNO_WIFI_SSID}/
 * {@code JUNO_WIFI_PASSWORD} (resolved by Juno itself at compile time, never written to a file).
 *
 * <p>{@code LedCanvas.drawText} unrolls a message into {@code drawChar} calls at compile time, so it
 * only ever accepts a compile-time string literal — no use here, since both values are runtime HTTP
 * response data. Instead this builds an {@code int[]} of ASCII character codes by hand (fixed
 * literal prefixes plus digits computed with plain division/remainder, or characters copied straight
 * out of a {@link Json#getString} buffer — Juno has no runtime String, so there is no other way to
 * turn either value into text) and calls {@link LedCanvas#drawChar} once per character itself,
 * exactly what {@code drawText} would have done if its message weren't required to be a literal.
 *
 * <p>WorldClockAPI's {@code currentDateTime} is a fixed-width ISO-8601 string
 * ({@code "2026-09-20T01:35+02:00"}); the hour/minute are read directly out of its known character
 * positions (11-12 and 14-15) rather than parsed generically. Open-Meteo's
 * {@code current.temperature_2m} is a JSON number with a decimal fraction (e.g. {@code 21.2});
 * {@link Json#getInt} reads only the leading integer digits, so the displayed value is the
 * temperature truncated toward zero, not rounded.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class MadridWeather {
    private static final String TIME_HOST = "worldclockapi.com";
    private static final int TIME_PORT = 80;
    private static final String TIME_PATH = "/api/json/cet/now";
    private static final int TIME_HOUR_INDEX = 11;
    private static final int TIME_MINUTE_INDEX = 14;

    private static final String WEATHER_HOST = "api.open-meteo.com";
    private static final int WEATHER_PORT = 80;
    private static final String WEATHER_PATH = "/v1/forecast?latitude=40.4168&longitude=-3.7038"
            + "&current=temperature_2m&timezone=Europe%2FMadrid";

    private static final int CHAR_SPACING = 6;
    private static final int MAX_MESSAGE_CHARS = 24;

    private static int appendChar(int[] chars, int index, int code) {
        chars[index] = code;
        return index + 1;
    }

    /** Appends the base-10 digits of {@code value} (with a leading '-' if negative) and returns the new index. */
    private static int appendNumber(int[] chars, int index, int value) {
        if (value < 0) {
            index = appendChar(chars, index, '-');
            value = -value;
        }
        int digitCount = 1;
        int probe = value;
        while (probe >= 10) {
            probe = probe / 10;
            digitCount = digitCount + 1;
        }
        int divisor = 1;
        int step = 1;
        while (step < digitCount) {
            divisor = divisor * 10;
            step = step + 1;
        }
        int remaining = value;
        int digit = 0;
        while (digit < digitCount) {
            int place = remaining / divisor;
            index = appendChar(chars, index, '0' + place);
            remaining = remaining - place * divisor;
            divisor = divisor / 10;
            digit = digit + 1;
        }
        return index;
    }

    private static void scrollMessage(boolean[][] frame, int[] chars, int messageLength) {
        int messageWidth = messageLength * CHAR_SPACING;
        int startOffset = LedCanvas.WIDTH;
        int endOffset = -messageWidth;
        int offset = startOffset;
        while (offset >= endOffset) {
            LedCanvas.clear(frame);
            int i = 0;
            while (i < messageLength) {
                LedCanvas.drawChar(frame, chars[i], offset + i * CHAR_SPACING, 0);
                i = i + 1;
            }
            LedCanvas.show(frame);
            Delay.millis(80);
            LedMatrix.clear();
            offset = offset - 1;
        }
    }

    public static void main(String[] args) {
        Delay.millis(2000);
        Wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));

        while (Wifi.status() != Wifi.STATUS_CONNECTED) {
            Delay.millis(1000);
        }

        LedMatrix.begin();
        boolean[][] frame = new boolean[LedCanvas.HEIGHT][LedCanvas.WIDTH];
        // Allocated once, outside the loop, and overwritten every iteration: Juno's arena never frees
        // memory, so fresh allocations inside a while(true) would exhaust it after a few hundred cycles.
        byte[] response = new byte[512];
        byte[] timeText = new byte[32];
        int[] message = new int[MAX_MESSAGE_CHARS];

        while (true) {
            int timeBytes = HttpClient.get(TIME_HOST, TIME_PORT, TIME_PATH, response, response.length);
            if (timeBytes > 0) {
                int timeTextLength = Json.getString(response, response.length, "currentDateTime",
                        timeText, timeText.length);
                if (timeTextLength > TIME_MINUTE_INDEX + 1) {
                    int length = 0;
                    length = appendChar(message, length, 'M');
                    length = appendChar(message, length, 'a');
                    length = appendChar(message, length, 'd');
                    length = appendChar(message, length, 'r');
                    length = appendChar(message, length, 'i');
                    length = appendChar(message, length, 'd');
                    length = appendChar(message, length, ' ');
                    length = appendChar(message, length, 'T');
                    length = appendChar(message, length, 'i');
                    length = appendChar(message, length, 'm');
                    length = appendChar(message, length, 'e');
                    length = appendChar(message, length, ':');
                    length = appendChar(message, length, ' ');
                    length = appendChar(message, length, timeText[TIME_HOUR_INDEX]);
                    length = appendChar(message, length, timeText[TIME_HOUR_INDEX + 1]);
                    length = appendChar(message, length, ':');
                    length = appendChar(message, length, timeText[TIME_MINUTE_INDEX]);
                    length = appendChar(message, length, timeText[TIME_MINUTE_INDEX + 1]);

                    scrollMessage(frame, message, length);
                }
            }

            int weatherBytes = HttpClient.get(WEATHER_HOST, WEATHER_PORT, WEATHER_PATH,
                    response, response.length);
            if (weatherBytes > 0) {
                int temperatureCelsius = Json.getInt(response, response.length, "current.temperature_2m");

                int length = 0;
                length = appendChar(message, length, 'M');
                length = appendChar(message, length, 'a');
                length = appendChar(message, length, 'd');
                length = appendChar(message, length, 'r');
                length = appendChar(message, length, 'i');
                length = appendChar(message, length, 'd');
                length = appendChar(message, length, ' ');
                length = appendChar(message, length, 'W');
                length = appendChar(message, length, 'e');
                length = appendChar(message, length, 'a');
                length = appendChar(message, length, 't');
                length = appendChar(message, length, 'h');
                length = appendChar(message, length, 'e');
                length = appendChar(message, length, 'r');
                length = appendChar(message, length, ':');
                length = appendChar(message, length, ' ');
                length = appendNumber(message, length, temperatureCelsius);
                length = appendChar(message, length, 'C');

                scrollMessage(frame, message, length);
            }

            Delay.millis(1000);
        }
    }
}
