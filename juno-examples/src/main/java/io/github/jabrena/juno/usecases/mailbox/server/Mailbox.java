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
 * <p>The mailbox is available at {@code /api/v1/mailbox/}. Messages are stored as
 * {@code /mailbox/YYYYMMDDHHMMSS/<uuid>.txt}; files outside that timestamp-folder layout are ignored.
 * Only the small index is kept in the 8 KiB board arena.
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
        MailboxView view = new MailboxView();
        model.begin();
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
            String ip = settings.qIp() != null ? settings.qIp() : "SET q.ip IN application.properties";
            view.drawIp(ip);
            printLocalIP(ip);
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

        MailboxController controller = new MailboxController(model, view);
        byte[] requestBody = new byte[MailboxModel.MAX_REQUEST_BYTES];
        while (true) {
            int bodyLength = HttpServer.accept(requestBody, requestBody.length);
            if (bodyLength >= 0) {
                controller.handle(HttpServer.method(), HttpServer.path(), HttpServer.contentLength(), requestBody,
                        bodyLength);
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
