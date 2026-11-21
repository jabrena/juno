package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.util.concurrent.ExecutionException;

/**
 * The {@link HttpLatencyBenchmark} job over HTTPS ({@value #HTTPS_URL}): the same requests, up to
 * {@value HttpLatencyBenchmark#MAX_REQUESTS} (two on the UNO R4 WiFi, built with {@code -Djuno.Xss=5120
 * -Djuno.Xmx=7168}), one after another, on virtual threads and as subtasks of a structured scope.
 *
 * <p>Each request now also runs a TLS handshake, which is CPU-bound and needs far more memory than a plain socket, so
 * this benchmark shows what the parallel modes gain when part of every request computes rather than waits, and whether
 * the board has room for several TLS sessions at once.
 */
@Board({ArduinoNanoEsp32Board.class, ArduinoUnoR4WiFiBoard.class, AiThinkerEsp32CamBoard.class,
        M5StackCardputerBoard.class, RaspberryPico2WifiBoard.class, ArduinoPortentaH7Board.class})
public final class HttpsLatencyBenchmark {
    static final String HTTPS_URL = "https://httpbin.org/delay/1";

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        // Serial first, so a failure while the benchmark is built can still be reported.
        Serial.begin(BaudRate.BAUD_115200);
        new HttpLatencyBenchmark("HTTPS", HTTPS_URL).run();
    }
}
