package io.github.jabrena.juno.api.hardware.net.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.camera.Camera;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Properties;

import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * Serves the AI Thinker ESP32-CAM's camera over Wi-Fi. {@code GET /} is a page with the live view; {@code GET /stream}
 * is an MJPEG stream of JPEG frames, one part per frame. While streaming, the newest frame is also saved to the
 * microSD card as {@code /frames/latest.jpg} every ten seconds.
 *
 * <p>The card needs {@code wifi.ssid} and {@code wifi.password} in {@code application.properties}, as in
 * {@link io.github.jabrena.juno.api.hardware.net.AiThinkerEsp32CamWifiFromSd}. Open {@code http://<address>/} in a
 * browser on the same network. One viewer streams at a time: the JDK {@code HttpServer} subset serves one connection
 * at a time, and the stream ends when the viewer closes it, as the next write then throws {@code IOException}. The
 * frame buffer and the server's request buffer need more than the default 8 KiB arena:
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash -Djuno.Xmx=16k \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.http.AiThinkerEsp32CamStream -Djuno.board=ai-thinker-esp32-cam
 * }</pre>
 */
@Board(AiThinkerEsp32CamBoard.class)
public final class AiThinkerEsp32CamStream {
    private static final Wifi WIFI = MCU.getWifi();
    private static final SdCard SD_CARD = MCU.getSdCard();
    private static final Camera CAMERA = MCU.getCamera();
    private static final Clock CLOCK = MCU.getClock();
    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int HTTP_PORT = 80;
    private static final int FRAME_CAPACITY = 4096;
    private static final int SAVE_INTERVAL_MILLIS = 10000;
    private static final String CREDENTIALS_FILE = "application.properties";
    private static final String FRAME_FILE = "/frames/latest.jpg";
    private static final String STREAM_CONTENT_TYPE = "multipart/x-mixed-replace; boundary=frame";
    private static final String PART_PREFIX = "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ";
    private static final String PART_HEADER_END = "\r\n\r\n";
    private static final String PART_END = "\r\n";
    private static final String PAGE = "<html><body><h1>ESP32-CAM</h1><img src=\"/stream\"></body></html>";
    private static final int[] LOCAL_IP = new int[4];
    private static final byte[] FRAME = new byte[FRAME_CAPACITY];
    private static final byte[] DIGITS = new byte[8];

    private AiThinkerEsp32CamStream() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        if (!CAMERA.begin()) {
            Serial.println("Camera initialization failed");
            return;
        }
        if (!connectWifi()) {
            return;
        }
        printLocalIp();
        SD_CARD.createDirectory("/frames");

        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 4);
        server.createContext("/stream", AiThinkerEsp32CamStream::stream);
        server.createContext("/", AiThinkerEsp32CamStream::page);
        server.start();
        Serial.println("Listening on port 80");
        while (true) {
            Delay.millis(10000);
        }
    }

    private static void page(HttpExchange exchange) throws IOException {
        boolean found = exchange.getRequestMethod().equals("GET") && exchange.getRequestURI().toString().equals("/");
        String body = found ? PAGE : "not found";
        exchange.getResponseHeaders().set("Content-Type", found ? "text/html" : "text/plain");
        exchange.sendResponseHeaders(found ? HTTP_OK : HTTP_NOT_FOUND, body.length());
        try (OutputStream output = exchange.getResponseBody()) {
            writeText(output, body);
        }
    }

    /**
     * Sends one MJPEG part per captured frame until the viewer disconnects, saving the newest frame every interval.
     * The response has no length, so it ends when the connection closes.
     */
    private static void stream(HttpExchange exchange) throws IOException {
        Serial.println("GET /stream started");
        exchange.getResponseHeaders().set("Content-Type", STREAM_CONTENT_TYPE);
        exchange.sendResponseHeaders(HTTP_OK, 0);
        try (OutputStream output = exchange.getResponseBody()) {
            int lastSaveMillis = CLOCK.millis();
            while (true) {
                int length = CAMERA.capture(FRAME);
                if (length < 0) {
                    Delay.millis(20);
                    continue;
                }
                int digitCount = writeDecimal(DIGITS, length);
                writeText(output, PART_PREFIX);
                output.write(DIGITS, 0, digitCount);
                writeText(output, PART_HEADER_END);
                output.write(FRAME, 0, length);
                writeText(output, PART_END);
                int now = CLOCK.millis();
                if (now - lastSaveMillis >= SAVE_INTERVAL_MILLIS) {
                    lastSaveMillis = now;
                    if (SD_CARD.writeBytes(FRAME_FILE, FRAME, length)) {
                        Serial.println("Saved /frames/latest.jpg");
                    } else {
                        Serial.println("Could not save the frame");
                    }
                }
            }
        } catch (IOException closed) {
            Serial.println("GET /stream ended");
        }
    }

    private static void writeText(OutputStream output, String text) throws IOException {
        for (int index = 0; index < text.length(); index++) {
            output.write(text.charAt(index));
        }
    }

    /** Writes {@code value} in decimal at the start of {@code out} and returns how many digits it used. */
    private static int writeDecimal(byte[] out, int value) {
        int digits = 1;
        int rest = value;
        while (rest >= 10) {
            rest = rest / 10;
            digits = digits + 1;
        }
        int remaining = value;
        for (int i = digits - 1; i >= 0; i--) {
            out[i] = (byte) ('0' + remaining % 10);
            remaining = remaining / 10;
        }
        return digits;
    }

    private static boolean connectWifi() throws IOException {
        String ssid;
        String password;
        try (InputStream file = SD_CARD.open(CREDENTIALS_FILE)) {
            if (file == null) {
                Serial.println("application.properties not found");
                return false;
            }
            Properties properties = new Properties();
            properties.load(file);
            ssid = properties.getProperty("wifi.ssid");
            password = properties.getProperty("wifi.password");
        }
        if (ssid == null || password == null) {
            Serial.println("WiFi credentials are missing");
            return false;
        }
        WIFI.begin(ssid, password);
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Serial.print("WiFi connection failed; status=");
            Serial.println(WIFI.status());
            return false;
        }
        return true;
    }

    private static void printLocalIp() {
        WIFI.localIP(LOCAL_IP);
        Serial.print("WiFi connected; IP=");
        Serial.print(LOCAL_IP[0]);
        Serial.print(".");
        Serial.print(LOCAL_IP[1]);
        Serial.print(".");
        Serial.print(LOCAL_IP[2]);
        Serial.print(".");
        Serial.println(LOCAL_IP[3]);
    }
}
