package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpServer;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The mailbox composition root: shows the Java and Arduino intro, wires the SD-backed model,
 * HTTP controller and TFT view, then runs the server loop.
 *
 * <p>Wi-Fi credentials are loaded from {@code /application.properties} on the SD card:
 * {@code wifi.ssid=...} and {@code wifi.password=...}. On the UNO Q the optional {@code q.ip=...} gives the address to
 * display, because the Q's network address is assigned by its Linux side; it is read at runtime, so no board needs a
 * build-time environment variable.
 *
 * <p>The mailbox is available at {@code /api/v1/mailbox/}, and {@code GET /actuator/info} identifies the service.
 * Every mailbox request must carry the API key in a {@code Mailbox-API-Key} header, or it is answered {@code 401}; the
 * key starts as {@code 1234567890}, is changed from the Security screen (the cover's gear, then SECURITY) and is kept
 * in {@code /api-key.txt} on the SD card. The board serves plain HTTP, so the key keeps out casual callers on the
 * network, not someone reading its traffic.
 * Messages are stored as {@code /mailbox/YYYYMMDDHHMMSS/<uuid>.txt}; files outside that timestamp-folder layout are
 * ignored.
 * Only the small index is kept in the board arena.
 *
 * <p>Serving one message builds a response of about 1.3 KiB, which the default 8 KiB arena cannot fit beside the
 * startup objects: on the UNO R4 WiFi the first {@code GET /api/v1/mailbox/<uuid>} ran out of memory and froze the
 * board. Build it with {@code -Djuno.Xmx=11008}, close to the most the R4 links with: the API key's request-header
 * buffer took the room 11k had left, and a larger arena overlaps the stack.
 */
@Board({ArduinoUnoQBoard.class, ArduinoUnoR4WiFiBoard.class})
public final class Mailbox {
    private static final String APPLICATION_PROPERTIES = "application.properties";
    private static final String WIFI_SSID = "wifi.ssid";
    private static final String WIFI_PASSWORD = "wifi.password";
    private static final String Q_IP = "q.ip";
    private static final Wifi WIFI = ArduinoUno.getWifi();
    private static final int ADDRESS_POLL_MILLIS = 250;
    private static final int ADDRESS_POLLS = 80;
    private static final int TOUCH_POLL_MILLIS = 100;

    private Mailbox() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        new MailboxIntroView().show();

        SdCard sdCard = TftTouchShield.getSdCard();
        MailboxModel model = new MailboxModel(sdCard);
        MailboxSecurity security = new MailboxSecurity(sdCard);
        MailboxView view = new MailboxView(security);
        model.begin();
        security.begin();
        view.drawCover(model.storageReady(), model.messageCount());

        if (!model.storageReady()) {
            view.drawInternetOffline();
            Serial.println("Mailbox stopped: SD card is unavailable");
            return;
        }
        MailboxSettings settings = loadSettings(sdCard);
        if (settings == null) {
            view.drawInternetOffline();
            Serial.println("Mailbox stopped: WiFi credentials are unavailable");
            return;
        }
        connectWifi(settings);
        if (ArduinoUno.getBoardModel().isQ()) {
            view.drawIp(settings.qIp());
            printLocalIP(settings.qIp() != null ? settings.qIp() : "q.ip is not set");
        } else {
            int[] ip = new int[4];
            if (!waitForAddress(ip)) {
                view.drawInternetOffline();
                Serial.println("Mailbox stopped: no IP address from DHCP");
                return;
            }
            view.drawIp(ip);
            printLocalIP(ip);
        }
        view.drawInternetOnline();
        HttpServer.begin(80);
        Serial.println("Mailbox listening on port 80");

        MailboxController controller = new MailboxController(model, security, view);
        byte[] requestBody = new byte[MailboxModel.MAX_REQUEST_BYTES];
        Clock clock = ArduinoUno.getClock();
        int nextTouchPoll = clock.millis();
        boolean touched = false;
        while (true) {
            int acceptStarted = clock.millis();
            int bodyLength = HttpServer.accept(requestBody, requestBody.length);
            if (bodyLength >= 0) {
                // One line per request: the time to read it and the time to answer it, to find where clients wait.
                int handleStarted = clock.millis();
                String method = HttpServer.method();
                controller.handle(method, HttpServer.path(), HttpServer.contentLength(), requestBody, bodyLength);
                Serial.print("HTTP ");
                Serial.print(method);
                Serial.print(" read ms ");
                Serial.print(handleStarted - acceptStarted);
                Serial.print(" handle ms ");
                Serial.println(clock.millis() - handleStarted);
            }
            // accept() returns at once when no client waits, so the screen is polled between requests; a tap counts
            // once, when the press starts.
            int now = clock.millis();
            if (now - nextTouchPoll >= 0) {
                nextTouchPoll = now + TOUCH_POLL_MILLIS;
                boolean pressed = TftTouchShield.readTouch();
                if (pressed && !touched) {
                    Serial.print("Touch ");
                    Serial.print(TftTouchShield.touchX());
                    Serial.print(",");
                    Serial.println(TftTouchShield.touchY());
                    view.touch(TftTouchShield.touchX(), TftTouchShield.touchY());
                }
                touched = pressed;
            }
        }
    }

    private static MailboxSettings loadSettings(SdCard sdCard) {
        try (InputStream file = sdCard.open(APPLICATION_PROPERTIES)) {
            if (file == null) {
                Serial.println("application.properties not found");
                return null;
            }
            Properties properties = new Properties();
            properties.load(file);
            String ssid = properties.getProperty(WIFI_SSID);
            String password = properties.getProperty(WIFI_PASSWORD);
            if (ssid == null || password == null) {
                Serial.println("WiFi credentials are missing");
                return null;
            }
            return new MailboxSettings(ssid, password, properties.getProperty(Q_IP));
        } catch (IOException exception) {
            Serial.println("Could not read application.properties");
            return null;
        }
    }

    private static void connectWifi(MailboxSettings settings) {
        Serial.println("Starting WiFi");
        WIFI.begin(settings.ssid(), settings.password());
        while (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Delay.millis(500);
        }
        Serial.println("WiFi connected");
    }

    /**
     * WiFiS3 reports connected once it joins the network, before DHCP assigns an address, so {@code localIP} briefly
     * reads 0.0.0.0. Polls until the address arrives or about 20 seconds pass.
     */
    private static boolean waitForAddress(int[] ip) {
        WIFI.localIP(ip);
        for (int poll = 0; ip[0] == 0 && poll < ADDRESS_POLLS; poll++) {
            Delay.millis(ADDRESS_POLL_MILLIS);
            WIFI.localIP(ip);
        }
        return ip[0] != 0;
    }

    private static void printLocalIP(int[] ip) {
        Serial.print("IP: ");
        Serial.print(ip[0]);
        Serial.print(".");
        Serial.print(ip[1]);
        Serial.print(".");
        Serial.print(ip[2]);
        Serial.print(".");
        Serial.println(ip[3]);
    }

    private static void printLocalIP(String ip) {
        Serial.print("IP: ");
        Serial.println(ip);
    }

    /** The settings from {@code application.properties}; {@code qIp} is {@code null} when not configured. */
    private record MailboxSettings(String ssid, String password, String qIp) {
    }
}
