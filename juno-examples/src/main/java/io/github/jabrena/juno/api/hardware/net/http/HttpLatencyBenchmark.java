package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;

/**
 * Times an I/O-bound job, {@value #REQUESTS} requests to a public endpoint that answers after a fixed second
 * ({@value #HTTP_URL}), done three ways on the Nano ESP32: one after another, on {@value #REQUESTS} virtual threads, and
 * as {@value #REQUESTS} subtasks of a {@link StructuredTaskScope}.
 *
 * <p>Unlike a CPU-bound job, a request spends almost all its time waiting for the server, so threads pay off even on
 * one core: while one request waits, another can be sent. The ESP32 runtime lets up to four HTTP requests be in flight
 * at once. Plain HTTP keeps TLS handshakes, which are CPU-bound, out of the measurement; the endpoint receives nothing
 * but the request line. {@link HttpsLatencyBenchmark} runs the same job over HTTPS.
 *
 * <p>Each request owns its {@link HttpClient}, built once at startup, so two requests never share a response. A
 * request records its status, or {@code -1} when it fails, instead of throwing, so it runs as a plain task in every
 * mode. Every round prints, per mode, the elapsed milliseconds, how many requests answered 200 (a timing is only
 * trusted when all did) and the speed-up over the sequential time. Rounds repeat after a pause long enough for a serial
 * monitor started after the upload to attach, and for the public endpoint not to be hammered.
 */
@Board(ArduinoNanoEsp32Board.class)
public final class HttpLatencyBenchmark {
    static final String HTTP_URL = "http://httpbin.org/delay/1";
    static final int REQUESTS = 4;
    private static final int ROUND_PAUSE_MILLIS = 15_000;
    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    /** The three ways the same requests are made, in the order they are timed; the sequential one comes first. */
    private enum Mode {
        SEQUENTIAL,
        THREADS,
        SCOPE;

        // The board's enums carry only int-like fields and have no name(), so the label is a method.
        String label() {
            return switch (this) {
                case SEQUENTIAL -> "sequential";
                case THREADS -> "threads";
                case SCOPE -> "scope";
            };
        }
    }

    // values() copies the array on every call, so it is read once.
    private static final Mode[] MODES = Mode.values();

    /** One request with its own client, and the status of its last attempt. */
    private static final class Request {
        private final HttpClient client = HttpClient.newHttpClient();
        private final HttpRequest request;
        private int status;

        Request(String url) {
            this.request = HttpRequest.newBuilder(URI.create(url)).build();
        }

        void fetch() {
            try {
                status = client.send(request, HttpResponse.BodyHandlers.ofByteArray()).statusCode();
            } catch (IOException | InterruptedException failure) {
                status = -1;
            }
        }
    }

    private final Wifi wifi = MCU.getWifi();
    private final Clock clock = MCU.getClock();
    private final String name;
    private final String url;
    private final Request first;
    private final Request second;
    private final Request third;
    private final Request fourth;

    /** A benchmark that prints {@code name} at the start of each round and requests {@code url}. */
    HttpLatencyBenchmark(String name, String url) {
        this.name = name;
        this.url = url;
        this.first = new Request(url);
        this.second = new Request(url);
        this.third = new Request(url);
        this.fourth = new Request(url);
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        new HttpLatencyBenchmark("HTTP", HTTP_URL).run();
    }

    void run() throws InterruptedException, ExecutionException {
        Serial.begin(BaudRate.BAUD_115200);
        if (!connectWifi()) {
            return;
        }
        for (int round = 1; ; round++) {
            Thread.sleep(ROUND_PAUSE_MILLIS);
            Serial.println(name + " latency benchmark round " + round + ": " + REQUESTS + " x " + url);
            int sequential = 0;
            for (int index = 0; index < MODES.length; index++) {
                Mode mode = MODES[index];
                int start = clock.millis();
                fetchAll(mode);
                int elapsed = Math.max(clock.millis() - start, 1);
                if (mode == Mode.SEQUENTIAL) {
                    sequential = elapsed;
                }
                String speedUp = mode == Mode.SEQUENTIAL ? "" : " speed-up=" + hundredths(sequential * 100 / elapsed) + "x";
                int answered = answered();
                // A timing only counts when every request answered; otherwise the statuses say which did not.
                String statuses = answered == REQUESTS ? "" : " statuses=" + first.status + "," + second.status + ","
                        + third.status + "," + fourth.status;
                Serial.println(mode.label() + " elapsed=" + elapsed + "ms ok=" + answered + "/" + REQUESTS + speedUp
                        + statuses);
            }
        }
    }

    /** Makes every request the way {@code mode} names, and returns once all have answered or failed. */
    private void fetchAll(Mode mode) throws InterruptedException, ExecutionException {
        switch (mode) {
            case SEQUENTIAL -> {
                first.fetch();
                second.fetch();
                third.fetch();
                fourth.fetch();
            }
            case THREADS -> {
                Thread firstThread = Thread.startVirtualThread(() -> first.fetch());
                Thread secondThread = Thread.startVirtualThread(() -> second.fetch());
                Thread thirdThread = Thread.startVirtualThread(() -> third.fetch());
                Thread fourthThread = Thread.startVirtualThread(() -> fourth.fetch());
                firstThread.join();
                secondThread.join();
                thirdThread.join();
                fourthThread.join();
            }
            case SCOPE -> {
                try (var scope = StructuredTaskScope.open()) {
                    scope.fork(() -> first.fetch());
                    scope.fork(() -> second.fetch());
                    scope.fork(() -> third.fetch());
                    scope.fork(() -> fourth.fetch());
                    // A request records its own failure, so no subtask throws and join() never reports one.
                    scope.join();
                }
            }
        }
    }

    /** How many requests answered 200 in the last mode. */
    private int answered() {
        return ok(first) + ok(second) + ok(third) + ok(fourth);
    }

    private static int ok(Request request) {
        return request.status == 200 ? 1 : 0;
    }

    /** Formats a value in hundredths, such as 187 as {@code 1.87}. */
    private static String hundredths(int value) {
        int fraction = value % 100;
        return (value / 100) + (fraction < 10 ? ".0" : ".") + fraction;
    }

    private boolean connectWifi() {
        wifi.begin(MCU.getSecretStore().getString("wifi.ssid"), MCU.getSecretStore().getString("wifi.password"));
        int attempts = 0;
        while (wifi.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        boolean connected = wifi.status() == Wifi.STATUS_CONNECTED;
        Serial.println(connected ? "wifi connected" : "wifi connection failed");
        return connected;
    }
}
