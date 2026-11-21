package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.Watchdog;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.camera.Camera;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpMethod;
import io.github.jabrena.juno.hardware.net.http.HttpServer;
import io.github.jabrena.juno.hardware.net.http.HttpStatus;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Serves the AI Thinker ESP32-CAM's camera over Wi-Fi. {@code GET /} is a page with the live view; {@code GET /stream}
 * is an MJPEG stream of 160x120 JPEG frames, one part per frame; {@code GET /capture} takes one fresh 640x480 photo
 * on every request and returns it as a single JPEG, then puts the sensor back to the stream's size.
 *
 * <p>The Wi-Fi credentials come from the board's secret store (its NVS partition), provisioned once with
 * {@code juno:secret} as {@code wifi.ssid} and {@code wifi.password}. Open {@code http://<address>/} in a browser on
 * the same network. One viewer streams at a time.
 *
 * <p>The OV2640's connection on the ribbon can be flaky: a camera start that fails is retried every two seconds,
 * and one that hangs inside the driver stops feeding the watchdog, which reboots the board after 15 s.
 *
 * <p>The 48 KB frame buffer does not fit the default 8 KB arena: build with {@code -Djuno.Xmx=63k}.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash -Djuno.board=ai-thinker-esp32-cam -Djuno.port=<PORT> \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.http.AiThinkerEsp32CamStream -Djuno.Xmx=63k
 * }</pre>
 */
@Board(AiThinkerEsp32CamBoard.class)
@Watchdog(timeoutMillis = 15000)
public final class AiThinkerEsp32CamStream {
    private static final Wifi WIFI = MCU.getWifi();
    private static final Camera CAMERA = MCU.getCamera();
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
            + "<p><a href=\"/capture\">640x480 capture</a></p></body></html>";
    private static final int[] LOCAL_IP = new int[4];

    private AiThinkerEsp32CamStream() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
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
            } else if (method.equals(HttpMethod.GET) && path.equals("/capture")) {
                sendCapture(frame);
            } else if (method.equals(HttpMethod.GET) && path.equals("/")) {
                HttpServer.respond(HttpStatus.OK, "text/html", PAGE);
            } else {
                HttpServer.respond(HttpStatus.NOT_FOUND, "text/plain", "not found");
            }
        }
    }

    /** Takes one 640x480 photo and answers with it as a single JPEG; the sensor goes back to the stream's size. */
    private static void sendCapture(byte[] frame) {
        int length = -1;
        if (CAMERA.setFrameSize(Camera.FRAME_SIZE_VGA)) {
            for (int attempt = 0; attempt < CAPTURE_ATTEMPTS && length < 0; attempt++) {
                length = CAMERA.capture(frame);
            }
        }
        CAMERA.setFrameSize(Camera.FRAME_SIZE_QQVGA);
        if (length < 0) {
            Serial.println("GET /capture failed");
            HttpServer.respond(HttpStatus.INTERNAL_SERVER_ERROR, "text/plain", "capture failed");
            return;
        }
        Serial.print("GET /capture sent ");
        Serial.print(length);
        Serial.println(" bytes");
        HttpServer.beginStream(HttpStatus.OK, "image/jpeg");
        HttpServer.streamBytes(frame, length);
        HttpServer.endStream();
    }

    /** Sends one MJPEG part per captured frame until the viewer disconnects. */
    private static void streamFrames(byte[] frame, byte[] digits) {
        HttpServer.beginStream(HttpStatus.OK, STREAM_CONTENT_TYPE);
        int dropped = 0;
        while (true) {
            int length = CAMERA.capture(frame);
            if (length < 0) {
                dropped = dropped + 1;
                if (dropped % DROPPED_FRAMES_REPORT == 0) {
                    Serial.print("Frames dropped (no frame, or larger than the buffer): ");
                    Serial.println(dropped);
                }
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
