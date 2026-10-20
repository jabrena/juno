package io.github.jabrena.juno.usecases.supabase.dashboard.services;

import io.github.jabrena.juno.api.hardware.MCU;
import io.github.jabrena.juno.api.hardware.net.Wifi;
import io.github.jabrena.juno.api.hardware.serial.Serial;
import io.github.jabrena.juno.api.hardware.time.Clock;
import io.github.jabrena.juno.api.mvc.Task;
import io.github.jabrena.juno.usecases.supabase.commons.SupabaseClient;
import io.github.jabrena.juno.usecases.supabase.commons.SupabaseContentRange;
import io.github.jabrena.juno.usecases.supabase.commons.SupabaseResponse;
import io.github.jabrena.juno.usecases.supabase.dashboard.commons.TemperatureDashboardState;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

/**
 * Connects Wi-Fi and reads up to two Supabase pages per controller frame, publishing progress and histogram values into the
 * dashboard model. Each scope reads two adjacent pages concurrently; the second page uses an offset from the same
 * keyset cursor, and both results are committed in timestamp order. The first page also carries the exact-count
 * preference, avoiding a standalone count request.
 */
public final class SupabaseTemperatureService implements Task {
    private static final Clock CLOCK = MCU.getClock();
    public static final int PAGE_ROWS = 50;

    private static final int WIFI_ATTEMPTS = 30;
    private static final int RETRY_DELAY_MILLIS = 1000;

    private final SupabaseClient supabase;
    private final SupabaseResponse response;
    private final int responseBodyCapacity;
    private final SupabaseClient nextSupabase;
    private final SupabaseResponse nextResponse;
    private final TemperatureDashboardState model;
    private final SensorPageScanner scanner = new SensorPageScanner();
    private final SensorPageScanner nextScanner = new SensorPageScanner();

    private int wifiAttempts;
    private int nextWifiCheck;
    private int retryAt;
    private int pages;
    private boolean reading;
    private boolean retrying;
    private boolean countRequested;
    private boolean started;
    private boolean done;

    public SupabaseTemperatureService(
            SupabaseClient supabase,
            SupabaseResponse response,
            int responseBodyCapacity,
            SupabaseClient nextSupabase,
            SupabaseResponse nextResponse,
            TemperatureDashboardState model) {
        this.supabase = supabase;
        this.response = response;
        this.responseBodyCapacity = responseBodyCapacity;
        this.nextSupabase = nextSupabase;
        this.nextResponse = nextResponse;
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
        nextWifiCheck = CLOCK.millis() + RETRY_DELAY_MILLIS;
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
        int now = CLOCK.millis();
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
        preparePageRequests();
        if (!executePagePair()) {
            return;
        }
        processPagePair();
    }

    private void preparePageRequests() {
        supabase.from("sensor-data")
                .select("ts,temp1,temp2,temp3");
        if (nextScanner.lastIdLength() > 0) {
            supabase.filter("ts", "gt", nextScanner.lastId(), nextScanner.lastIdLength());
        }
        supabase.order("ts")
                .limit(PAGE_ROWS);

        nextSupabase.from("sensor-data")
                .select("ts,temp1,temp2,temp3");
        if (nextScanner.lastIdLength() > 0) {
            nextSupabase.filter("ts", "gt", nextScanner.lastId(), nextScanner.lastIdLength());
        }
        nextSupabase.order("ts")
                .limit(PAGE_ROWS)
                .offset(PAGE_ROWS);
        if (!countRequested) {
            supabase.countExact();
        }
    }

    private boolean executePagePair() {
        try (var scope = StructuredTaskScope.open()) {
            var pageTask = scope.fork(() -> supabase.execute());
            var nextPageTask = scope.fork(() -> nextSupabase.execute());
            scope.join();
            pageTask.get();
            nextPageTask.get();
            return true;
        } catch (InterruptedException | ExecutionException failed) {
            // Task.step() cannot propagate interruption, and Juno intentionally exposes no currentThread() handle.
            Serial.println("Parallel Supabase gathering stopped");
            failRead();
            return false;
        }
    }

    private void processPagePair() {
        if (tlsFailed(response, supabase) || tlsFailed(nextResponse, nextSupabase)) {
            retrying = true;
            retryAt = CLOCK.millis() + RETRY_DELAY_MILLIS;
            Serial.println("TLS connection failed; retrying once");
            return;
        }
        if (!countRequested) {
            countRequested = true;
            int totalRows = SupabaseContentRange.total(response);
            if (totalRows >= 0) {
                model.publishTotalRows(totalRows, PAGE_ROWS);
            } else {
                Serial.println("Exact row count unavailable");
            }
        }
        int rows = processPage(response, scanner);
        if (rows < 0 || rows < PAGE_ROWS) {
            return;
        }
        processPage(nextResponse, nextScanner);
    }

    private void retryStep() {
        if (CLOCK.millis() - retryAt < 0) {
            return;
        }
        retrying = false;
        requestPage();
    }

    private int processPage(SupabaseResponse pageResponse, SensorPageScanner pageScanner) {
        Serial.print("Select status: ");
        Serial.println(pageResponse.statusCode());
        Serial.print("Body length: ");
        Serial.println(pageResponse.bodyLength());
        if (!pageResponse.successful()) {
            failRead();
            return -1;
        }
        if (pageResponse.bodyLength() == 0) {
            // PostgREST represents a successful empty select as "[]". A zero-byte 2xx response is incomplete HTTP.
            Serial.println("Empty page response");
            failRead();
            return -1;
        }
        if (pageResponse.bodyLength() >= responseBodyCapacity) {
            Serial.println("Page exceeds the response buffer");
            failRead();
            return -1;
        }
        int rows = pageScanner.scan(pageResponse.bodyBuffer(), pageResponse.bodyLength(), model);
        if (rows < 0) {
            Serial.println("Malformed page");
            failRead();
            return -1;
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
        return rows;
    }

    private static boolean tlsFailed(SupabaseResponse pageResponse, SupabaseClient pageClient) {
        return pageResponse.statusCode() == 0 && pageResponse.bodyLength() < 0 && pageClient.ready();
    }

    private void failRead() {
        done = true;
        model.publishPartial();
    }
}
