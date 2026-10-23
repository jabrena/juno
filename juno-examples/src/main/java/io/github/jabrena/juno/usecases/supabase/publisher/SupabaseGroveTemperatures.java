package io.github.jabrena.juno.usecases.supabase.publisher;

import io.github.jabrena.juno.annotations.ArduinoOneQBoard;
import io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoOne;
import io.github.jabrena.juno.hardware.device.grove.GroveBaseShield;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveDht11;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveDht22;
import io.github.jabrena.juno.hardware.device.grove.sensors.GroveTemperatureSensorV12;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpClient;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import java.time.LocalDateTime;

/**
 * Reads three Grove temperature sensors once a minute and inserts them as one {@code sensor-data} row through
 * Supabase's PostgREST API: {@code temp1} from the DHT22-format Temperature and Humidity Sensor Pro on D4,
 * {@code temp2} from the DHT11 on D2, and {@code temp3} from the Temperature Sensor v1.2 on A0. {@code ts} is UTC from
 * {@link LocalDateTime#now()} once at startup and then advanced by {@link SensorClock}, so no extra time requests are
 * made; {@code id} is left to the table's default. A sensor that fails to read is sent as {@code null}.
 *
 * <p>Build-time environment variables provide {@code JUNO_WIFI_SSID}, {@code JUNO_WIFI_PASSWORD},
 * {@code SUPABASE_API_URL} (the table endpoint, such as {@code https://abcdefgh.supabase.co/rest/v1/sensor-data})
 * and {@code SUPABASE_API_KEY}. A board is a public client: use a publishable key protected by Row Level Security,
 * never a secret or legacy {@code service_role} key.
 *
 * <p>All buffers, the header builder and the row writer are allocated once at startup; the loop allocates nothing.
 * Wi-Fi, TLS and the sensor drivers together exceed the UNO WiFi Rev2's 48 KiB of flash, so the program targets the
 * UNO R4 WiFi and UNO Q.
 */
@Board({ArduinoOneR4WiFiBoard.class, ArduinoOneQBoard.class})
public final class SupabaseGroveTemperatures {
    private static final Wifi WIFI = ArduinoOne.getWifi();

    private static final GroveBaseShield SHIELD = new GroveBaseShield();
    private static final Clock CLOCK = ArduinoOne.getClock();
    private static final int DHT22_PIN = SHIELD.getD4();
    private static final int DHT11_PIN = SHIELD.getD2();
    private static final int THERMISTOR_PIN = SHIELD.getA0();
    private static final int THERMISTOR_ADC_MAXIMUM = 1023;
    private static final int REQUEST_HEADERS_CAPACITY = 144;
    private static final int REQUEST_BODY_CAPACITY = 96;
    private static final int RESPONSE_BODY_CAPACITY = 160;
    private static final int RESPONSE_HEADERS_CAPACITY = 64;
    private static final int WIFI_ATTEMPTS = 30;
    private static final int TIME_ATTEMPTS = 120;
    private static final int UPLOAD_INTERVAL_MILLIS = 60_000;

    private final GroveDht22 dht22 = new GroveDht22();
    private final GroveDht11 dht11 = new GroveDht11();
    private final GroveTemperatureSensorV12 thermistor = new GroveTemperatureSensorV12();
    private final int[] dht22Reading = new int[GroveDht22.READING_LENGTH];
    private final int[] dht11Reading = new int[GroveDht11.READING_LENGTH];
    private final int[] frame = new int[GroveDht22.FRAME_LENGTH];
    private final byte[] requestHeaders = new byte[REQUEST_HEADERS_CAPACITY];
    private final byte[] requestBody = new byte[REQUEST_BODY_CAPACITY];
    private final byte[] responseBody = new byte[RESPONSE_BODY_CAPACITY];
    private final byte[] responseHeaders = new byte[RESPONSE_HEADERS_CAPACITY];
    private final int[] status = new int[2];
    private final HttpHeaders headers = new HttpHeaders(requestHeaders, REQUEST_HEADERS_CAPACITY);
    private final SensorClock clock = new SensorClock();
    private final SensorDataRow row = new SensorDataRow(requestBody, REQUEST_BODY_CAPACITY);
    private final GroveTemperatureRecord record = new GroveTemperatureRecord();
    private int requestHeadersLength;
    private boolean headersPrepared;

    private SupabaseGroveTemperatures() {
    }

    public static void main(String[] args) {
        SupabaseGroveTemperatures application = new SupabaseGroveTemperatures();
        if (!application.connect()) {
            return;
        }
        if (!application.initializeClock()) {
            return;
        }

        while (true) {
            GroveTemperatureRecord reading = application.retrieveData();
            application.insertToSupabase(reading);
            Delay.millis(UPLOAD_INTERVAL_MILLIS);
        }
    }

    /** Connects Wi-Fi. */
    private boolean connect() {
        Serial.begin(BaudRate.BAUD_115200);

        WIFI.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));

        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < WIFI_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("WiFi connection failed");
            return false;
        }

        return true;
    }

    /** Synchronizes the application clock with internet time. */
    private boolean initializeClock() {
        LocalDateTime start = LocalDateTime.now();
        int timeAttempts = 0;
        while (start.equals(LocalDateTime.MIN) && timeAttempts < TIME_ATTEMPTS) {
            Delay.millis(1000);
            start = LocalDateTime.now();
            timeAttempts = timeAttempts + 1;
        }
        if (start.equals(LocalDateTime.MIN)) {
            Serial.println("Internet time unavailable");
            return false;
        }
        clock.start(start.getYear(), start.getMonthValue(), start.getDayOfMonth(),
                start.getHour(), start.getMinute(), start.getSecond(), CLOCK.millis());

        return true;
    }

    /** Reads all Grove sensors into and returns the reusable current reading record. */
    private GroveTemperatureRecord retrieveData() {
        float temp1 = SensorDataRow.NO_READING;
        if (dht22.read(DHT22_PIN, dht22Reading, dht22Reading.length, frame, frame.length)) {
            temp1 = dht22Reading[GroveDht22.TEMPERATURE_TENTHS_CELSIUS] / 10.0f;
        }
        float temp2 = SensorDataRow.NO_READING;
        if (dht11.read(DHT11_PIN, dht11Reading, dht11Reading.length, frame, frame.length)) {
            temp2 = dht11Reading[GroveDht11.TEMPERATURE_CELSIUS];
        }
        float temp3 = SensorDataRow.NO_READING;
        int adc = thermistor.rawValue(THERMISTOR_PIN);
        if (adc > 0 && adc < THERMISTOR_ADC_MAXIMUM) {
            temp3 = thermistor.celsiusFromRaw(adc);
        }

        clock.update(CLOCK.millis());
        record.set(temp1, temp2, temp3,
                clock.year(), clock.month(), clock.day(),
                clock.hour(), clock.minute(), clock.second());
        return record;
    }

    /** Serializes one reading and inserts it into Supabase. */
    private void insertToSupabase(GroveTemperatureRecord reading) {
        if (!headersPrepared) {
            headersPrepared = true;
            headers.add("apikey", System.getenv("SUPABASE_API_KEY"))
                    .add("Content-Type", "application/json")
                    .add("Prefer", "return=minimal");
            requestHeadersLength = headers.length();
            if (requestHeadersLength < 0) {
                Serial.println("Request headers too long");
                return;
            }
        }
        int bodyLength = row.write(reading.temp1(), reading.temp2(), reading.temp3(),
                reading.year(), reading.month(), reading.day(),
                reading.hour(), reading.minute(), reading.second());
        if (bodyLength < 0) {
            Serial.println("Request body too long");
            return;
        }
        int responseLength = HttpClient.post(System.getenv("SUPABASE_API_URL"),
                requestHeaders, requestHeadersLength,
                row.buffer(), bodyLength,
                responseBody, RESPONSE_BODY_CAPACITY,
                responseHeaders, RESPONSE_HEADERS_CAPACITY,
                status);
        printTimestamp(reading);
        Serial.print(" Insert status: ");
        Serial.println(status[0]);
        if (status[0] < 200 || status[0] >= 300) {
            Serial.print("Response: ");
            printBody(responseBody, responseLength);
        }
    }

    /** Prints the UTC time of the cycle as {@code YYYY-MM-DDTHH:MM:SSZ}, the same value sent in {@code ts}. */
    private static void printTimestamp(GroveTemperatureRecord reading) {
        Serial.print(reading.year());
        Serial.print("-");
        printTwoDigits(reading.month());
        Serial.print("-");
        printTwoDigits(reading.day());
        Serial.print("T");
        printTwoDigits(reading.hour());
        Serial.print(":");
        printTwoDigits(reading.minute());
        Serial.print(":");
        printTwoDigits(reading.second());
        Serial.print("Z");
    }

    private static void printTwoDigits(int value) {
        if (value < 10) {
            Serial.print("0");
        }
        Serial.print(value);
    }

    /** Prints the response as decimal ASCII codes: no String is built, so the error path stays allocation-free. */
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
