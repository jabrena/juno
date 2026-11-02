package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.http.HttpServer;
import io.github.jabrena.juno.hardware.net.http.HttpStatus;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;

/**
 * HTTPS on the UNO Q: serves port 443 through the bridge's plain listener, with the TLS session run on the MCU.
 * {@code GET /status} answers {@code online}, every other request 404, and each request's time goes to the serial log.
 * The Q's Linux side is already on the network, so the program joins nothing.
 *
 * <p>The certificate and key are PEM text read from the root of the Data Logger Shield's microSD card at startup:
 * {@code server.crt} and {@code server.key}. Each file must fit in 2048 bytes. Create them once on a computer:
 * <pre>{@code
 * openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 3650 \
 *   -subj /CN=juno.local -keyout server.key -out server.crt
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.http.QHttpsServer -Djuno.board=arduino-uno-q
 * curl --cacert server.crt --resolve juno.local:443:<address> https://juno.local/status
 * }</pre>
 */
@Board({ArduinoUnoQBoard.class})
public final class QHttpsServer {
    private static final int HTTPS_PORT = 443;
    private static final int PEM_CAPACITY = 2048;

    private static final SdCard SD_CARD = new DataLoggerShield().getSdCard();
    private static final byte[] CERTIFICATE = new byte[PEM_CAPACITY];
    private static final byte[] KEY = new byte[PEM_CAPACITY];

    private QHttpsServer() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        Clock clock = ArduinoUno.getClock();

        if (!SD_CARD.begin()) {
            Serial.println("SD card initialization failed");
            return;
        }
        int certificateLength = loadCertificate();
        int keyLength = loadKey();
        if (certificateLength <= 0 || keyLength <= 0) {
            Serial.println("server.crt or server.key is missing, empty or larger than 2048 bytes");
            return;
        }
        if (!HttpServer.beginTls(HTTPS_PORT, CERTIFICATE, certificateLength, KEY, keyLength)) {
            Serial.println("Certificate or key did not parse");
            return;
        }
        for (int index = 0; index < keyLength; index++) {
            KEY[index] = 0;
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

    /** Reads server.crt into the certificate buffer; its length, or 0 when it is missing, empty or too large. */
    private static int loadCertificate() {
        try (InputStream file = SD_CARD.open("server.crt")) {
            if (file == null) {
                return 0;
            }
            int length = 0;
            while (length < CERTIFICATE.length) {
                int count = file.read(CERTIFICATE, length, CERTIFICATE.length - length);
                if (count < 0) {
                    break;
                }
                if (count == 0) {
                    return 0;
                }
                length += count;
            }
            if (length == CERTIFICATE.length && file.available() > 0) {
                return 0;
            }
            return length;
        } catch (IOException exception) {
            return 0;
        }
    }

    /** Reads server.key into the key buffer; its length, or 0 when it is missing, empty or too large. */
    private static int loadKey() {
        try (InputStream file = SD_CARD.open("server.key")) {
            if (file == null) {
                return 0;
            }
            int length = 0;
            while (length < KEY.length) {
                int count = file.read(KEY, length, KEY.length - length);
                if (count < 0) {
                    break;
                }
                if (count == 0) {
                    return 0;
                }
                length += count;
            }
            if (length == KEY.length && file.available() > 0) {
                return 0;
            }
            return length;
        } catch (IOException exception) {
            return 0;
        }
    }
}
