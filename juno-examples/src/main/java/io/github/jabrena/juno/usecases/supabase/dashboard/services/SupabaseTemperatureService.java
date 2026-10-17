package io.github.jabrena.juno.usecases.supabase.dashboard.services;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.mvc.Task;
import io.github.jabrena.juno.api.net.Wifi;
import io.github.jabrena.juno.usecases.supabase.commons.SupabaseClient;
import io.github.jabrena.juno.usecases.supabase.commons.SupabaseContentRange;
import io.github.jabrena.juno.usecases.supabase.commons.SupabaseResponse;
import io.github.jabrena.juno.usecases.supabase.dashboard.commons.TemperatureDashboardState;

/**
 * Connects Wi-Fi and reads one Supabase page per controller frame, publishing progress and histogram values into the
 * dashboard model. It uses {@link Task} instead of a thread so UNO R4 WiFi needs no extra thread stack.
 */
public final class SupabaseTemperatureService implements Task {
    public static final int PAGE_ROWS = 50;

    private static final int WIFI_ATTEMPTS = 30;
    private static final int RETRY_DELAY_MILLIS = 1000;

    private final SupabaseClient supabase;
    private final SupabaseResponse response;
    private final int responseBodyCapacity;
    private final TemperatureDashboardState model;
    private final SensorPageScanner scanner = new SensorPageScanner();

    private int wifiAttempts;
    private int nextWifiCheck;
    private int retryAt;
    private int pages;
    private boolean reading;
    private boolean retrying;
    private boolean started;
    private boolean done;

    public SupabaseTemperatureService(
            SupabaseClient supabase,
            SupabaseResponse response,
            int responseBodyCapacity,
            TemperatureDashboardState model) {
        this.supabase = supabase;
        this.response = response;
        this.responseBodyCapacity = responseBodyCapacity;
        this.model = model;
    }

    /** Starts the non-blocking Wi-Fi connection phase before the frame loop begins. */
    public void begin() {
        if (started) {
            return;
        }
        started = true;
        Serial.println("Connecting WiFi");
        Wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        nextWifiCheck = Clock.millis() + RETRY_DELAY_MILLIS;
    }

    /** Advances Wi-Fi setup, a delayed TLS retry, or one page read. */
    @Override
    public void step() {
        if (!started || done) {
            return;
        }
        if (!reading) {
            connectStep();
        } else if (retrying) {
            retryStep();
        } else {
            requestPage();
        }
    }

    private void connectStep() {
        if (Wifi.status() == Wifi.STATUS_CONNECTED) {
            reading = true;
            model.publishReading();
            Serial.println("WiFi connected");
            return;
        }
        int now = Clock.millis();
        if (now - nextWifiCheck < 0) {
            return;
        }
        wifiAttempts = wifiAttempts + 1;
        nextWifiCheck = now + RETRY_DELAY_MILLIS;
        if (wifiAttempts >= WIFI_ATTEMPTS) {
            done = true;
            model.publishWifiFailed();
            Serial.println("WiFi connection failed");
        }
    }

    private void requestPage() {
        supabase.from("sensor-data")
                .select("ts,temp1,temp2,temp3");
        if (model.totalRows() < 0) {
            supabase.countExact();
        }
        if (scanner.lastIdLength() > 0) {
            supabase.filter("ts", "gt", scanner.lastId(), scanner.lastIdLength());
        }
        supabase.order("ts")
                .limit(PAGE_ROWS);
        supabase.execute();
        if (response.statusCode() == 0 && response.bodyLength() < 0 && supabase.ready()) {
            retrying = true;
            retryAt = Clock.millis() + RETRY_DELAY_MILLIS;
            Serial.println("TLS connection failed; retrying once");
            return;
        }
        processPage();
    }

    private void retryStep() {
        if (Clock.millis() - retryAt < 0) {
            return;
        }
        retrying = false;
        supabase.execute();
        processPage();
    }

    private void processPage() {
        Serial.print("Select status: ");
        Serial.println(response.statusCode());
        Serial.print("Body length: ");
        Serial.println(response.bodyLength());
        if (!response.successful()) {
            failRead();
            return;
        }
        if (response.bodyLength() >= responseBodyCapacity) {
            Serial.println("Page exceeds the response buffer");
            failRead();
            return;
        }
        int rows = scanner.scan(response.bodyBuffer(), response.bodyLength(), model);
        if (rows < 0) {
            Serial.println("Malformed page");
            failRead();
            return;
        }
        if (model.totalRows() < 0) {
            int totalRows = SupabaseContentRange.total(response);
            if (totalRows >= 0) {
                model.publishTotalRows(totalRows, PAGE_ROWS);
            }
        }
        pages = pages + 1;
        model.publishPage(pages, rows);
        Serial.print("Page ");
        Serial.print(pages);
        Serial.print(": rows=");
        Serial.println(rows);
        if (rows < PAGE_ROWS) {
            done = true;
            model.publishComplete();
        }
    }

    private void failRead() {
        done = true;
        model.publishPartial();
    }
}
