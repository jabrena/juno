package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.serial.Serial;
import io.github.jabrena.juno.api.net.Wifi;
import io.github.jabrena.juno.api.net.http.HttpClient;
import java.util.concurrent.StructuredTaskScope;

/**
 * A task stack overflowed inside the runtime's C code, not in a generated method (whose own check would catch it):
 * the path-buffer HTTPS call copies the path into a 256-byte local, more than a 512-byte task stack has left. Such a
 * frame can allocate straight past the stack's canary without writing it, and printing the report from the overflowed
 * stack would push further into other memory; the runtime must still stop with its overflow message instead of
 * resetting the board. RuntimeStackOverflow.expected holds the output by hand.
 */
@Board(ArduinoUnoR3WiFiRev2.class)
public final class RuntimeStackOverflow {
    static final int BODY = 32;
    static final int HEADERS = 32;

    public static void main(String[] args) throws Exception {
        Wifi.begin("juno", "secret");
        byte[] path = {'/', 'r', 'e', 'a', 'd', 'i', 'n', 'g', 's'};
        byte[] body = new byte[BODY];
        byte[] headers = new byte[HEADERS];
        int[] status = new int[2];
        Serial.println("before");
        try (var scope = StructuredTaskScope.open()) {
            scope.fork(() -> {
                HttpClient.get("https://example.com", path, 9, body, BODY, headers, HEADERS, status);
            });
            scope.join();
        }
        Serial.println("not reached");
    }
}
