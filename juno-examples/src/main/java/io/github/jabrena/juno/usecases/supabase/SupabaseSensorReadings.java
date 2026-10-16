package io.github.jabrena.juno.usecases.supabase;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Wifi;
import io.github.jabrena.juno.api.net.http.Json;

/**
 * Inserts one sensor reading through Supabase's PostgREST API, then reads one matching row back. Create a
 * {@code sensor_readings} table with integer {@code temperature} and {@code humidity} columns, grant the
 * {@code anon} role select/insert access, and enable matching Row Level Security policies.
 *
 * <p>Build-time environment variables provide {@code JUNO_WIFI_SSID}, {@code JUNO_WIFI_PASSWORD},
 * {@code JUNO_SUPABASE_HOST} (for example {@code abcdefgh.supabase.co}, without {@code https://}), and
 * {@code JUNO_SUPABASE_PUBLISHABLE_KEY}. A board is a public client: never use a Supabase secret or legacy
 * {@code service_role} key in firmware.
 *
 * <p>All arrays and the two client objects are allocated once at startup. Requests reuse those buffers, and the
 * Wi-Fi wait loop allocates nothing. The deliberately small capacities also fit the UNO WiFi Rev2's 1 KiB arena;
 * larger applications can supply larger caller-owned response buffers on the UNO R4 WiFi and UNO Q.
 */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoUnoR3WiFiRev2.class})
public final class SupabaseSensorReadings {
    private static final int PATH_CAPACITY = 160;
    private static final int REQUEST_HEADERS_CAPACITY = 128;
    private static final int REQUEST_BODY_CAPACITY = 64;
    private static final int RESPONSE_BODY_CAPACITY = 192;
    private static final int RESPONSE_HEADERS_CAPACITY = 96;
    private static final int WIFI_ATTEMPTS = 30;

    private SupabaseSensorReadings() {
    }

    public static void main(String[] args) {
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
            return;
        }

        byte[] path = new byte[PATH_CAPACITY];
        byte[] requestHeaders = new byte[REQUEST_HEADERS_CAPACITY];
        byte[] requestBody = new byte[REQUEST_BODY_CAPACITY];
        byte[] responseBody = new byte[RESPONSE_BODY_CAPACITY];
        byte[] responseHeaders = new byte[RESPONSE_HEADERS_CAPACITY];
        int[] status = new int[2];

        SupabaseResponse response = new SupabaseResponse(
                responseBody, RESPONSE_BODY_CAPACITY,
                responseHeaders, RESPONSE_HEADERS_CAPACITY,
                status);
        SupabaseClient supabase = new SupabaseClient(
                System.getenv("JUNO_SUPABASE_HOST"),
                System.getenv("JUNO_SUPABASE_PUBLISHABLE_KEY"),
                path, PATH_CAPACITY,
                requestHeaders, REQUEST_HEADERS_CAPACITY,
                requestBody, REQUEST_BODY_CAPACITY,
                response);

        response = supabase.from("sensor_readings")
                .insert("{\"temperature\":23,\"humidity\":60}")
                .execute();
        Serial.print("Insert status: ");
        Serial.println(response.statusCode());
        if (!response.successful()) {
            return;
        }

        response = supabase.from("sensor_readings")
                .select("temperature,humidity")
                .eq("temperature", "23")
                .limit(1)
                .execute();
        Serial.print("Select status: ");
        Serial.println(response.statusCode());
        if (!response.successful()) {
            return;
        }

        Serial.print("Temperature: ");
        Serial.println(Json.getInt(response.bodyBuffer(), response.bodyLength(), "[0].temperature"));
        Serial.print("Humidity: ");
        Serial.println(Json.getInt(response.bodyBuffer(), response.bodyLength(), "[0].humidity"));
    }
}
