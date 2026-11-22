package io.github.jabrena.juno.api.hardware.net.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.Watchdog;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.camera.Camera;
import io.github.jabrena.juno.hardware.device.camera.FrameSize;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * Serves the AI Thinker ESP32-CAM's camera over Wi-Fi. {@code GET /} is a page with the live view; {@code GET /stream}
 * is an MJPEG stream of 160x120 JPEG frames, one part per frame; {@code GET /capture} takes one fresh 640x480 photo
 * on every request and returns it as a single JPEG, then puts the sensor back to the stream's size.
 * {@code GET /capture/flash} does the same with the white flash LED on GPIO4 lit while the photo is taken.
 *
 * <p>The Wi-Fi credentials come from the board's secret store (its NVS partition), provisioned once with
 * {@code juno:secret} as {@code wifi.ssid} and {@code wifi.password}. Open {@code http://<address>/} in a browser on
 * the same network. One viewer streams at a time: the JDK {@code HttpServer} subset serves one connection at a time,
 * and the stream ends when the viewer closes it, as the next write then throws {@code IOException}.
 *
 * <p>The OV2640's connection on the ribbon can be flaky: a camera start that fails is retried every two seconds,
 * and one that hangs inside the driver stops feeding the watchdog, which reboots the board after 15 s.
 *
 * <p>The 48 KB frame buffer does not fit the default 8 KB arena: build with {@code -Djuno.Xmx=59k}.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash -Djuno.board=ai-thinker-esp32-cam -Djuno.port=<PORT> \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.http.AiThinkerEsp32CamStream -Djuno.Xmx=59k
 * }</pre>
 */
@Board(AiThinkerEsp32CamBoard.class)
@Watchdog(timeoutMillis = 15000)
public final class AiThinkerEsp32CamStream {
    private static final Wifi WIFI = MCU.getWifi();
    private static final Camera CAMERA = MCU.getCamera();
    private static final Gpio GPIO = MCU.getGpio();
    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int CAMERA_RETRY_MILLIS = 2000;
    private static final int HTTP_PORT = 80;
    /**
     * Room for one JPEG frame, shared by the stream and {@code /capture} since the server answers one request at a
     * time: a 640x480 photo takes tens of kilobytes, and a frame that does not fit is dropped.
     */
    private static final int FRAME_CAPACITY = 49152;
    private static final int CAPTURE_ATTEMPTS = 3;
    private static final int DROPPED_FRAMES_REPORT = 50;
    private static final String STREAM_CONTENT_TYPE = "multipart/x-mixed-replace; boundary=frame";
    private static final String PART_PREFIX = "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ";
    private static final String PART_HEADER_END = "\r\n\r\n";
    private static final String PART_END = "\r\n";
    private static final String PAGE = "<html><body><h1>ESP32-CAM</h1><img src=\"/stream\">"
            + "<p><a href=\"/capture\">640x480 capture</a> | <a href=\"/capture/flash\">with flash</a></p>"
            + "</body></html>";
    private static final int[] LOCAL_IP = new int[4];
    private static final byte[] FRAME = new byte[FRAME_CAPACITY];
    private static final byte[] DIGITS = new byte[8];
    /** The white flash LED: GPIO4, which the board numbers as {@code D4}. */
    private static int flashLed;

    private AiThinkerEsp32CamStream() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        flashLed = GPIO.digitalPin("D4");
        GPIO.pinMode(flashLed, GPIO.getOutput());
        GPIO.digitalWrite(flashLed, false);
        Serial.println("Starting camera");
        int attempts = 1;
        while (!CAMERA.begin()) {
            Serial.print("Camera initialization failed, attempt ");
            Serial.print(attempts);
            Serial.println("; retrying (check the camera ribbon)");
            attempts = attempts + 1;
            Delay.millis(CAMERA_RETRY_MILLIS);
        }
        Serial.println("Camera ready");
        if (!connectWifi()) {
            return;
        }
        printLocalIp();

        HttpServer server = HttpServer.create(new InetSocketAddress(HTTP_PORT), 4);
        server.createContext("/stream", AiThinkerEsp32CamStream::stream);
        server.createContext("/capture/flash", AiThinkerEsp32CamStream::flashCapture);
        server.createContext("/capture", AiThinkerEsp32CamStream::capture);
        server.createContext("/", AiThinkerEsp32CamStream::page);
        server.start();
        Serial.println("Listening on port 80");
        while (true) {
            Delay.millis(10000);
        }
    }

    private static void page(HttpExchange exchange) throws IOException {
        boolean found = exchange.getRequestMethod().equals("GET") && exchange.getRequestURI().toString().equals("/");
        sendText(exchange, found ? HTTP_OK : HTTP_NOT_FOUND, found ? "text/html" : "text/plain",
                found ? PAGE : "not found");
    }

    private static void capture(HttpExchange exchange) throws IOException {
        sendCapture(exchange, false);
    }

    private static void flashCapture(HttpExchange exchange) throws IOException {
        sendCapture(exchange, true);
    }

    /**
     * Takes one 640x480 photo and answers with it as a single JPEG; the sensor goes back to the stream's size. With
     * {@code flash}, the flash LED is lit for the photo: the first frame after switching size is dropped anyway, so
     * the one returned is exposed with the light on.
     */
    private static void sendCapture(HttpExchange exchange, boolean flash) throws IOException {
        int length = -1;
        if (flash) {
            GPIO.digitalWrite(flashLed, true);
        }
        if (CAMERA.setFrameSize(FrameSize.VGA)) {
            for (int attempt = 0; attempt < CAPTURE_ATTEMPTS && length < 0; attempt++) {
                length = CAMERA.capture(FRAME);
            }
        }
        GPIO.digitalWrite(flashLed, false);
        CAMERA.setFrameSize(FrameSize.QQVGA);
        if (length < 0) {
            Serial.println("GET /capture failed");
            sendText(exchange, HTTP_INTERNAL_ERROR, "text/plain", "capture failed");
            return;
        }
        Serial.print(flash ? "GET /capture/flash sent " : "GET /capture sent ");
        Serial.print(length);
        Serial.println(" bytes");
        exchange.getResponseHeaders().set("Content-Type", "image/jpeg");
        exchange.sendResponseHeaders(HTTP_OK, length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(FRAME, 0, length);
        }
    }

    /** Sends one MJPEG part per captured frame until the viewer disconnects; the response has no length. */
    private static void stream(HttpExchange exchange) throws IOException {
        Serial.println("GET /stream started");
        exchange.getResponseHeaders().set("Content-Type", STREAM_CONTENT_TYPE);
        exchange.sendResponseHeaders(HTTP_OK, 0);
        try (OutputStream output = exchange.getResponseBody()) {
            int dropped = 0;
            while (true) {
                int length = CAMERA.capture(FRAME);
                if (length < 0) {
                    dropped = dropped + 1;
                    if (dropped % DROPPED_FRAMES_REPORT == 0) {
                        Serial.print("Frames dropped (no frame, or larger than the buffer): ");
                        Serial.println(dropped);
                    }
                    Delay.millis(20);
                    continue;
                }
                int digitCount = writeDecimal(DIGITS, length);
                writeText(output, PART_PREFIX);
                output.write(DIGITS, 0, digitCount);
                writeText(output, PART_HEADER_END);
                output.write(FRAME, 0, length);
                writeText(output, PART_END);
            }
        } catch (IOException closed) {
            Serial.println("GET /stream ended");
        }
    }

    private static void sendText(HttpExchange exchange, int status, String contentType, String body)
            throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, body.length());
        try (OutputStream output = exchange.getResponseBody()) {
            writeText(output, body);
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

    private static boolean connectWifi() {
        String ssid = MCU.getSecretStore().getString("wifi.ssid");
        String password = MCU.getSecretStore().getString("wifi.password");
        if (ssid == null || password == null) {
            Serial.println("wifi.ssid or wifi.password missing from the secret store: run juno:secret");
            return false;
        }
        Serial.print("Connecting to Wi-Fi ");
        Serial.println(ssid);
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
