package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.hardware.BoardModel;
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
 * Times an I/O-bound job, up to {@value #MAX_REQUESTS} requests to a public endpoint that answers after a fixed second
 * ({@value #HTTP_URL}), done three ways: one after another, on one virtual thread per request, and as one subtask of a
 * {@link StructuredTaskScope} per request. The UNO R4 WiFi makes two requests, the other boards four
 * ({@link #requestCount}); build it with {@code -Djuno.Xss=5120 -Djuno.Xmx=7168}, as one request needs more than its
 * default 2 KB carrier stack (see {@code documentation/esp32-concurrency-improvements.md}, "UNO R4 WiFi").
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
@Board({ArduinoNanoEsp32Board.class, ArduinoUnoR4WiFiBoard.class, M5StackCardputerBoard.class,
        RaspberryPico2WifiBoard.class, ArduinoPortentaH7Board.class})
public final class HttpLatencyBenchmark {
    static final String HTTP_URL = "http://httpbin.org/delay/1";
    // An array length must be a compile-time constant on the board: the arrays hold the most requests any board makes.
    static final int MAX_REQUESTS = 4;
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
    private final int count = requestCount();
    private final Request[] requests = new Request[MAX_REQUESTS];
    private final Thread[] threads = new Thread[MAX_REQUESTS];

    /** A benchmark that prints {@code name} at the start of each round and requests {@code url}. */
    HttpLatencyBenchmark(String name, String url) {
        this.name = name;
        this.url = url;
        for (int index = 0; index < count; index++) {
            requests[index] = new Request(url);
        }
    }

    /**
     * How many requests a round makes on this board: two on the UNO R4 WiFi, as many as it lets be in flight, whose
     * 32 KB of RAM fits two parked requests with a 5 KB carrier stack and a 7 KiB arena; four elsewhere.
     */
    private static int requestCount() {
        return MCU.getBoardModel() == BoardModel.UNO_R4_WIFI ? 2 : MAX_REQUESTS;
    }

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        // Serial first, so a failure while the benchmark is built can still be reported.
        Serial.begin(BaudRate.BAUD_115200);
        new HttpLatencyBenchmark("HTTP", HTTP_URL).run();
    }

    void run() throws InterruptedException, ExecutionException {
        if (!connectWifi()) {
            return;
        }
        for (int round = 1; ; round++) {
            Thread.sleep(ROUND_PAUSE_MILLIS);
            Serial.println(name + " latency benchmark round " + round + ": " + count + " x " + url);
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
                String statuses = answered == count ? "" : " statuses=" + statuses();
                Serial.println(mode.label() + " elapsed=" + elapsed + "ms ok=" + answered + "/" + count + speedUp
                        + statuses);
            }
        }
    }

    /** Makes every request the way {@code mode} names, and returns once all have answered or failed. */
    private void fetchAll(Mode mode) throws InterruptedException, ExecutionException {
        switch (mode) {
            case SEQUENTIAL -> {
                for (int index = 0; index < count; index++) {
                    requests[index].fetch();
                }
            }
            case THREADS -> {
                for (int index = 0; index < count; index++) {
                    Request request = requests[index];
                    threads[index] = Thread.startVirtualThread(() -> request.fetch());
                }
                for (int index = 0; index < count; index++) {
                    threads[index].join();
                }
            }
            case SCOPE -> {
                try (var scope = StructuredTaskScope.open()) {
                    for (int index = 0; index < count; index++) {
                        Request request = requests[index];
                        scope.fork(() -> request.fetch());
                    }
                    // A request records its own failure, so no subtask throws and join() never reports one.
                    scope.join();
                }
            }
        }
    }

    /** How many requests answered 200 in the last mode. */
    private int answered() {
        int answered = 0;
        for (int index = 0; index < count; index++) {
            answered += ok(requests[index]);
        }
        return answered;
    }

    /** Every request's status in the last mode, comma-separated. */
    private String statuses() {
        String statuses = "" + requests[0].status;
        for (int index = 1; index < count; index++) {
            statuses = statuses + "," + requests[index].status;
        }
        return statuses;
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
