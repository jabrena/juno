package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.Esp32Cam;
import io.github.jabrena.juno.hardware.device.camera.Camera;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpMethod;
import io.github.jabrena.juno.hardware.net.http.HttpServer;
import io.github.jabrena.juno.hardware.net.http.HttpStatus;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Serves the AI Thinker ESP32-CAM's camera over Wi-Fi. {@code GET /} is a page with the live view; {@code GET /stream}
 * is an MJPEG stream of JPEG frames, one part per frame. While streaming, the newest frame is also saved to the
 * microSD card as {@code /frames/latest.jpg} every ten seconds.
 *
 * <p>The card needs {@code wifi.ssid} and {@code wifi.password} in {@code application.properties}, as in
 * {@link io.github.jabrena.juno.api.hardware.net.AiThinkerEsp32CamWifiFromSd}. Open {@code http://<address>/} in a
 * browser on the same network. One viewer streams at a time.
 */
@Board(AiThinkerEsp32CamBoard.class)
public final class AiThinkerEsp32CamStream {
    private static final Wifi WIFI = Esp32Cam.getWifi();
    private static final SdCard SD_CARD = Esp32Cam.getSdCard();
    private static final Camera CAMERA = Esp32Cam.getCamera();
    private static final Clock CLOCK = Esp32Cam.getClock();
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

        HttpServer.begin(HTTP_PORT);
        Serial.println("Listening on port 80");

        byte[] requestBody = new byte[16];
        byte[] frame = new byte[FRAME_CAPACITY];
        byte[] digits = new byte[8];
        while (true) {
            int bodyLength = HttpServer.accept(requestBody, requestBody.length);
            if (bodyLength < 0) {
                Delay.millis(5);
                continue;
            }
            String method = HttpServer.method();
            String path = HttpServer.path();
            if (method.equals(HttpMethod.GET) && path.equals("/stream")) {
                Serial.println("GET /stream started");
                streamFrames(frame, digits);
                Serial.println("GET /stream ended");
            } else if (method.equals(HttpMethod.GET) && path.equals("/")) {
                HttpServer.respond(HttpStatus.OK, "text/html", PAGE);
            } else {
                HttpServer.respond(HttpStatus.NOT_FOUND, "text/plain", "not found");
            }
        }
    }

    /** Sends one MJPEG part per captured frame until the viewer disconnects, saving the newest frame every interval. */
    private static void streamFrames(byte[] frame, byte[] digits) {
        HttpServer.beginStream(HttpStatus.OK, STREAM_CONTENT_TYPE);
        int lastSaveMillis = CLOCK.millis();
        while (true) {
            int length = CAMERA.capture(frame);
            if (length < 0) {
                Delay.millis(20);
                continue;
            }
            int digitCount = writeDecimal(digits, length);
            if (!HttpServer.streamText(PART_PREFIX)
                    || !HttpServer.streamBytes(digits, digitCount)
                    || !HttpServer.streamText(PART_HEADER_END)
                    || !HttpServer.streamBytes(frame, length)
                    || !HttpServer.streamText(PART_END)) {
                break;
            }
            int now = CLOCK.millis();
            if (now - lastSaveMillis >= SAVE_INTERVAL_MILLIS) {
                lastSaveMillis = now;
                if (SD_CARD.writeBytes(FRAME_FILE, frame, length)) {
                    Serial.println("Saved /frames/latest.jpg");
                } else {
                    Serial.println("Could not save the frame");
                }
            }
        }
        HttpServer.endStream();
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
