package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.M5StackCardputer;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpServer;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * {@link NanoHttpsServerSD} on the M5Stack Cardputer: joins the Wi-Fi network named in {@code application.properties}
 * on its microSD card, loads a self-signed certificate and its private key from the same card, then serves port 443: {@code GET /status} answers {@code online} and every other request answers 404.
 *
 * <p>Besides {@code wifi.ssid} and {@code wifi.password}, the card needs {@code server.crt} and {@code server.key} (PEM),
 * for example an EC P-256 pair made with:
 * <pre>{@code
 * openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes -days 3650 \
 *   -subj /CN=juno.local -keyout server.key -out server.crt
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.http.CardputerHttpsServerSD -Djuno.board=m5stack-cardputer
 * curl --cacert server.crt --resolve juno.local:443:<address> https://juno.local/status
 * }</pre>
 * The certificate and key are read into buffers allocated once at startup; the server parses them and keeps its own
 * copy, so the program never holds the key afterwards.
 */
@Board({M5StackCardputerBoard.class})
public final class CardputerHttpsServerSD {
    private static final Wifi WIFI = M5StackCardputer.getWifi();
    private static final SdCard SD_CARD = M5StackCardputer.getSd();

    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int HTTPS_PORT = 443;
    private static final int PEM_CAPACITY = 2048;

    private CardputerHttpsServerSD() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Credentials credentials = loadCredentials();
        if (credentials == null) {
            return;
        }
        byte[] certificate = new byte[PEM_CAPACITY];
        byte[] key = new byte[PEM_CAPACITY];
        int certificateLength = readPem(false, certificate);
        int keyLength = readPem(true, key);
        if (certificateLength <= 0 || keyLength <= 0) {
            Serial.println("server.crt or server.key missing");
            return;
        }
        if (!connectWifi(credentials.ssid(), credentials.password())) {
            return;
        }
        printLocalIp();

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
            int bodyLength = HttpServer.accept(body, body.length);
            if (bodyLength >= 0) {
                String path = HttpServer.path();
                if (path.equals("/status")) {
                    HttpServer.respond(200, "text/plain", "online");
                    Serial.println("GET /status answered 200");
                } else {
                    HttpServer.respond(404, "text/plain", "not found");
                    Serial.println("request answered 404");
                }
            }
        }
    }

    /**
     * Reads {@code server.key} (or {@code server.crt}) into {@code buffer} and returns its length, or -1 when it is
     * missing or does not fit. The paths stay literals, as {@code SdCard.open} requires.
     */
    private static int readPem(boolean key, byte[] buffer) throws IOException {
        try (InputStream file = key ? SD_CARD.open("server.key") : SD_CARD.open("server.crt")) {
            if (file == null) {
                return -1;
            }
            int length = 0;
            while (file.available() > 0) {
                int value = file.read();
                if (value < 0 || length == buffer.length) {
                    return -1;
                }
                buffer[length] = (byte) value;
                length = length + 1;
            }
            return length;
        }
    }

    private static boolean connectWifi(String ssid, String password) {
        WIFI.begin(ssid, password);
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("WiFi connection failed");
            return false;
        }
        Serial.println("WiFi connected");
        return true;
    }

    private static void printLocalIp() {
        int[] octets = new int[4];
        WIFI.localIP(octets);
        Serial.print("address=");
        Serial.print(octets[0]);
        Serial.print(".");
        Serial.print(octets[1]);
        Serial.print(".");
        Serial.print(octets[2]);
        Serial.print(".");
        Serial.println(octets[3]);
    }

    private static Credentials loadCredentials() throws IOException {
        if (!SD_CARD.begin()) {
            Serial.println("SD card initialization failed");
            return null;
        }
        try (InputStream file = SD_CARD.open("application.properties")) {
            if (file == null) {
                Serial.println("application.properties not found");
                return null;
            }
            Properties properties = new Properties();
            properties.load(file);
            String ssid = properties.getProperty("wifi.ssid");
            String password = properties.getProperty("wifi.password");
            if (ssid == null || password == null) {
                Serial.println("WiFi credentials are missing");
                return null;
            }
            return new Credentials(ssid, password);
        }
    }

    private record Credentials(String ssid, String password) {
    }
}
