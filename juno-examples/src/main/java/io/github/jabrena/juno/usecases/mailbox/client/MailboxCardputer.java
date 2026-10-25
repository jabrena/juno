package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.M5StackCardputer;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpClient;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.usecases.zelda.common.ux.Footer;
import io.github.jabrena.juno.usecases.zelda.common.ux.ScrollingText;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.mvc.KeyboardViewRouter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/** Mailbox client for the M5Stack Cardputer: settings, inbox, detail, and tweet-sized composition. */
@Board(M5StackCardputerBoard.class)
public final class MailboxCardputer {
    private static final String APPLICATION_PROPERTIES = "application.properties";
    private static final int SETTINGS_VIEW = 0;
    private static final int INBOX_VIEW = 1;
    private static final int FRAME_MILLIS = 50;
    private static final int WIFI_ATTEMPTS = 30;
    private static final int MESSAGE_BODY_TOP = 32;
    private static final int MESSAGE_BODY_HEIGHT = 74;
    /** The mailbox board can be slow to accept a connection while it serves another client or writes the SD card. */
    private static final int HTTP_TIMEOUT_MILLIS = 30000;
    private static final Wifi WIFI = M5StackCardputer.getWifi();

    private MailboxCardputer() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);

        Display display = M5StackCardputer.getDisplay();
        display.begin();
        Keyboard keyboard = M5StackCardputer.getKeyboard();
        keyboard.begin();

        MailboxSettings settings = loadSettings();
        if (settings == null || !connectWifi(settings.ssid(), settings.password(), display)) {
            return;
        }

        HttpClient.setTimeout(HTTP_TIMEOUT_MILLIS);
        MailboxModel model = new MailboxModel();
        MailboxClient client = new MailboxClient(model);
        KeyboardViewRouter router = new KeyboardViewRouter(FRAME_MILLIS, keyboard);
        Footer footer = new Footer(display);
        NumberedList messages = new NumberedList(display, model);
        router.register(SETTINGS_VIEW, new SettingsView(router, display, client, footer));
        router.register(INBOX_VIEW, new InboxView(router, display, model, client, messages, footer));
        ScrollingText body = new ScrollingText(display, MESSAGE_BODY_TOP, MESSAGE_BODY_HEIGHT);
        router.register(2, new MessageView(router, display, keyboard, model, client, body, footer));
        router.register(3, new ComposeView(router, display, keyboard, client, footer));
        router.background(client);
        router.start(SETTINGS_VIEW);
        router.run();
    }

    private static boolean connectWifi(String ssid, String password, Display display) {
        display.fillScreen(Display.BLACK);
        display.setTextColor(Display.YELLOW, Display.BLACK);
        display.setTextSize(2);
        display.setCursor(8, 30);
        display.print("Connecting WiFi");
        WIFI.begin(ssid, password);
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < WIFI_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
            display.setTextColor(Display.RED, Display.BLACK);
            display.setCursor(8, 65);
            display.print("WiFi failed");
            Serial.println("Mailbox client: WiFi failed");
            return false;
        }
        Serial.println("Mailbox client: WiFi connected");
        int[] octets = new int[4];
        WIFI.localIP(octets);
        printAddress("Mailbox client: IP ", octets);
        WIFI.gatewayIP(octets);
        printAddress("Mailbox client: gateway ", octets);
        return true;
    }

    private static void printAddress(String label, int[] octets) {
        Serial.print(label);
        for (int index = 0; index < octets.length; index++) {
            if (index > 0) {
                Serial.print(".");
            }
            Serial.print(octets[index]);
        }
        Serial.println("");
    }

    private static MailboxSettings loadSettings() {
        SdCard sd = M5StackCardputer.getSd();
        if (!sd.begin()) {
            Serial.println("Mailbox client: SD card unavailable");
            return null;
        }
        try (InputStream file = sd.open(APPLICATION_PROPERTIES)) {
            if (file == null) {
                Serial.println("Mailbox client: application.properties missing");
                return null;
            }
            Properties properties = new Properties();
            properties.load(file);
            String ssid = properties.getProperty("wifi.ssid");
            String password = properties.getProperty("wifi.password");
            if (ssid == null || password == null) {
                Serial.println("Mailbox client: wifi.ssid/password missing");
                return null;
            }
            return new MailboxSettings(ssid, password);
        } catch (IOException exception) {
            Serial.println("Mailbox client: could not read settings");
            return null;
        }
    }

    private record MailboxSettings(String ssid, String password) {
    }
}
