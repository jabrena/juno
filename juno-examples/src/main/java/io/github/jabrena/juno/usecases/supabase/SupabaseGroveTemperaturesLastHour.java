package io.github.jabrena.juno.usecases.supabase;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Wifi;

import java.time.LocalDateTime;

/**
 * Selects up to sixty {@code sensor-data} rows whose UTC {@code ts} is in the preceding hour. It is the read-side
 * companion to {@link SupabaseGroveTemperatures}: the query selects {@code ts}, {@code temp1}, {@code temp2}, and
 * {@code temp3}, using {@code ts=gte.<one-hour-ago>} in Supabase's PostgREST API. The sixty-row limit matches that
 * example's one-minute upload interval.
 *
 * <p>Build-time environment variables provide {@code JUNO_WIFI_SSID}, {@code JUNO_WIFI_PASSWORD},
 * {@code JUNO_SUPABASE_HOST} (for example {@code abcdefgh.supabase.co}, without {@code https://}), and
 * {@code JUNO_SUPABASE_PUBLISHABLE_KEY}. The table needs a Row Level Security policy granting the public client
 * {@code SELECT} access. Never embed a secret or legacy {@code service_role} key in firmware.
 *
 * <p>The response JSON is held in the 6 KiB caller-owned {@code responseBody} buffer. The serial monitor reports the
 * status and prints each response byte as its decimal ASCII code, so the board remains allocation-free; increase the
 * buffer if a full sixty-row response is larger than the configured capacity. This requires the UNO R4 WiFi or UNO Q
 * because of its HTTPS and buffer requirements.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class})
public final class SupabaseGroveTemperaturesLastHour {
    private static final int PATH_CAPACITY = 192;
    private static final int REQUEST_HEADERS_CAPACITY = 144;
    private static final int REQUEST_BODY_CAPACITY = 1;
    private static final int RESPONSE_BODY_CAPACITY = 6144;
    private static final int RESPONSE_HEADERS_CAPACITY = 128;
    private static final int TIMESTAMP_CAPACITY = 20;
    private static final int WIFI_ATTEMPTS = 30;
    private static final int TIME_ATTEMPTS = 120;
    private static final int MAXIMUM_READINGS = 60;

    private final byte[] cutoff = new byte[TIMESTAMP_CAPACITY];
    private final byte[] path = new byte[PATH_CAPACITY];
    private final byte[] requestHeaders = new byte[REQUEST_HEADERS_CAPACITY];
    private final byte[] requestBody = new byte[REQUEST_BODY_CAPACITY];
    private final byte[] responseBody = new byte[RESPONSE_BODY_CAPACITY];
    private final byte[] responseHeaders = new byte[RESPONSE_HEADERS_CAPACITY];
    private final int[] status = new int[2];
    private final SensorClock clock = new SensorClock();
    private final SupabaseResponse response = new SupabaseResponse(
            responseBody, RESPONSE_BODY_CAPACITY,
            responseHeaders, RESPONSE_HEADERS_CAPACITY,
            status);
    private final SupabaseClient supabase = new SupabaseClient(
            System.getenv("JUNO_SUPABASE_HOST"),
            System.getenv("JUNO_SUPABASE_PUBLISHABLE_KEY"),
            path, PATH_CAPACITY,
            requestHeaders, REQUEST_HEADERS_CAPACITY,
            requestBody, REQUEST_BODY_CAPACITY,
            response);

    private SupabaseGroveTemperaturesLastHour() {
    }

    public static void main(String[] args) {
        SupabaseGroveTemperaturesLastHour application = new SupabaseGroveTemperaturesLastHour();
        if (!application.connect()) {
            return;
        }
        if (!application.initializeClock()) {
            return;
        }
        application.retrieveData();
    }

    /** Connects Wi-Fi. */
    private boolean connect() {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);
        Wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));

        int attempts = 0;
        while (Wifi.status() != Wifi.STATUS_CONNECTED && attempts < WIFI_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (Wifi.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("WiFi connection failed");
            return false;
        }
        return true;
    }

    /** Synchronizes the application clock with internet time. */
    private boolean initializeClock() {
        LocalDateTime now = LocalDateTime.now();
        int timeAttempts = 0;
        while (now.equals(LocalDateTime.MIN) && timeAttempts < TIME_ATTEMPTS) {
            Delay.millis(1000);
            now = LocalDateTime.now();
            timeAttempts = timeAttempts + 1;
        }
        if (now.equals(LocalDateTime.MIN)) {
            Serial.println("Internet time unavailable");
            return false;
        }
        clock.start(now.getYear(), now.getMonthValue(), now.getDayOfMonth(),
                now.getHour(), now.getMinute(), now.getSecond(), Clock.millis());

        return true;
    }

    /** Retrieves and prints the sensor readings recorded during the preceding hour. */
    private void retrieveData() {
        int cutoffLength = clock.writeUtcMinusOneHour(cutoff, TIMESTAMP_CAPACITY);
        if (cutoffLength < 0) {
            Serial.println("Timestamp buffer too small");
            return;
        }

        supabase.from("sensor-data")
                .select("ts,temp1,temp2,temp3")
                .filter("ts", "gte", cutoff, cutoffLength)
                .limit(MAXIMUM_READINGS)
                .execute();
        Serial.print("Select status: ");
        Serial.println(response.statusCode());
        if (!response.successful()) {
            return;
        }
        Serial.print("Response bytes: ");
        Serial.println(response.bodyLength());
        printBody(response.bodyBuffer(), response.bodyLength());
    }

    /** Prints JSON response bytes as decimal ASCII codes without allocating a runtime string. */
    private static void printBody(byte[] body, int length) {
        int index = 0;
        while (index < length) {
            Serial.print(body[index]);
            Serial.print(" ");
            index = index + 1;
        }
        Serial.println("");
    }
}
