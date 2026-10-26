package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.net.http.HttpServer;
import io.github.jabrena.juno.hardware.net.http.HttpStatus;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

/**
 * HTTPS on the UNO Q: serves port 443 through the bridge's plain listener, with the TLS session run on the MCU.
 * {@code GET /status} answers {@code online}, every other request 404, and each request's time goes to the serial log.
 * The Q's Linux side is already on the network, so the program joins nothing.
 *
 * <p>The certificate and key are baked in at build time from {@code JUNO_TLS_CERT} and {@code JUNO_TLS_KEY} (PEM text),
 * so neither is committed:
 * <pre>{@code
 * openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 3650 \
 *   -subj /CN=juno.local -keyout server.key -out server.crt
 * JUNO_TLS_CERT="$(cat server.crt)" JUNO_TLS_KEY="$(cat server.key)" ./mvnw -f juno-examples/pom.xml compile \
 *   juno:upload -Djuno.main=io.github.jabrena.juno.api.hardware.net.http.QHttpsServer -Djuno.board=arduino-uno-q
 * curl --cacert server.crt --resolve juno.local:443:<address> https://juno.local/status
 * }</pre>
 */
@Board({ArduinoUnoQBoard.class})
public final class QHttpsServer {
    private static final int HTTPS_PORT = 443;
    private static final int PEM_CAPACITY = 2048;

    private QHttpsServer() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Clock clock = ArduinoUno.getClock();

        byte[] certificate = new byte[PEM_CAPACITY];
        byte[] key = new byte[PEM_CAPACITY];
        int certificateLength = copy(System.getenv("JUNO_TLS_CERT"), certificate);
        int keyLength = copy(System.getenv("JUNO_TLS_KEY"), key);
        if (!HttpServer.beginTls(HTTPS_PORT, certificate, certificateLength, key, keyLength)) {
            Serial.println("Certificate or key did not parse");
            return;
        }
        for (int index = 0; index < keyLength; index++) {
            key[index] = 0;
        }
        Serial.println("Listening on port 443");

        byte[] body = new byte[64];
        while (true) {
            int started = clock.millis();
            int bodyLength = HttpServer.accept(body, body.length);
            if (bodyLength >= 0) {
                if (HttpServer.path().equals("/status")) {
                    HttpServer.respond(HttpStatus.OK, "text/plain", "online");
                } else {
                    HttpServer.respond(HttpStatus.NOT_FOUND, "text/plain", "not found");
                }
                Serial.print("HTTPS request ms ");
                Serial.println(clock.millis() - started);
            }
        }
    }

    /** Copies a compile-time PEM text into {@code target}; its length, or 0 when it does not fit. */
    private static int copy(String pem, byte[] target) {
        if (pem.length() > target.length) {
            return 0;
        }
        for (int index = 0; index < pem.length(); index++) {
            target[index] = (byte) pem.charAt(index);
        }
        return pem.length();
    }
}
