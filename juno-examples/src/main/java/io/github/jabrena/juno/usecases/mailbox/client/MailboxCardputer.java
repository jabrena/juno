package io.github.jabrena.juno.usecases.mailbox.client;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpClient;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.usecases.mailbox.client.commons.MailboxModel;
import io.github.jabrena.juno.usecases.mailbox.client.commons.MailboxViews;
import io.github.jabrena.juno.usecases.mailbox.client.compose.ComposeView;
import io.github.jabrena.juno.usecases.mailbox.client.cover.CoverView;
import io.github.jabrena.juno.usecases.mailbox.client.inbox.InboxView;
import io.github.jabrena.juno.usecases.mailbox.client.inbox.NumberedList;
import io.github.jabrena.juno.usecases.mailbox.client.message.MessageView;
import io.github.jabrena.juno.usecases.mailbox.client.progress.ProgressView;
import io.github.jabrena.juno.usecases.mailbox.client.services.MailboxClient;
import io.github.jabrena.juno.usecases.mailbox.client.services.MailboxConnection;
import io.github.jabrena.juno.usecases.mailbox.client.services.MailboxKey;
import io.github.jabrena.juno.usecases.mailbox.client.services.MailboxPolling;
import io.github.jabrena.juno.usecases.mailbox.client.services.MailboxTrustedCertificate;
import io.github.jabrena.juno.usecases.mailbox.client.settings.SettingsView;
import io.github.jabrena.juno.usecases.mailbox.client.ux.Footer;
import io.github.jabrena.juno.usecases.mailbox.client.ux.ScrollingText;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.mvc.KeyboardViewRouter;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Mailbox client for the M5Stack Cardputer: a cover, settings, inbox, detail, and tweet-sized composition. Wi-Fi joins
 * in the background from the first frame, so the cover and settings respond while it connects. The cover opens the
 * settings: the API key (stored in {@code /api-key.txt} on the SD card, {@code 1234567890} until then), mailbox IP,
 * and polling interval. Saving enters the progress view while the mailbox is verified and loaded. The mailbox CA
 * certificate is read from {@code /mailbox-ca.crt} on the SD card before HTTPS starts. Mailbox requests carry the key
 * in a {@code Mailbox-API-Key} header over HTTPS; a board that rejects it shows "Wrong API key" in the inbox.
 *
 * <p>Its startup objects outgrow the default 8 KiB arena; the Cardputer has RAM to spare, so build it with
 * {@code -Djuno.Xmx=12k}.
 */
@Board(M5StackCardputerBoard.class)
public final class MailboxCardputer {
    private static final String APPLICATION_PROPERTIES = "application.properties";
    private static final int FRAME_MILLIS = 50;
    private static final int MESSAGE_BODY_TOP = 32;
    private static final int MESSAGE_BODY_HEIGHT = 74;
    private static final Wifi WIFI = MCU.getWifi();
    private static MailboxTrustedCertificate trustedCertificate;

    private MailboxCardputer() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(1000);

        Display display = MCU.getDisplay();
        Keyboard keyboard = MCU.getKeyboard();

        // The join starts right away: the SSID and password are runtime Strings, which must not be kept for later.
        SdCard sd = MCU.getSdCard();
        MailboxKey key = new MailboxKey(sd);
        MailboxPolling polling = new MailboxPolling(sd);
        MailboxSettings settings = loadSettings(sd, key, polling);
        trustedCertificate = new MailboxTrustedCertificate(sd);
        boolean trusted = trustedCertificate.load()
                && HttpClient.setTrustedCertificate(trustedCertificate.value(), trustedCertificate.length());
        if (!trusted) {
            Serial.println("Mailbox client: mailbox-ca.crt missing or invalid");
        }
        if (settings != null) {
            WIFI.begin(settings.ssid(), settings.password());
        }
        MailboxConnection connection = new MailboxConnection(WIFI, settings != null && trusted);

        HttpClient.setTimeout(MailboxClient.REQUEST_TIMEOUT_MILLIS);
        MailboxModel model = new MailboxModel();
        MailboxClient client = new MailboxClient(model, connection, key, polling);
        KeyboardViewRouter router = new KeyboardViewRouter(FRAME_MILLIS, keyboard);
        Footer footer = new Footer(display);
        NumberedList messages = new NumberedList(display, model);
        router.register(MailboxViews.COVER, new CoverView(router, display));
        ProgressView progress = new ProgressView(router, display, client, model, connection, footer);
        SettingsView settingsView = new SettingsView(
                router, display, keyboard, client, connection, key, polling, progress, footer);
        router.register(MailboxViews.SETTINGS, settingsView);
        router.register(MailboxViews.PROGRESS, progress);
        router.register(MailboxViews.INBOX, new InboxView(router, display, keyboard, model, progress, messages, footer));
        ScrollingText body = new ScrollingText(display, MESSAGE_BODY_TOP, MESSAGE_BODY_HEIGHT);
        router.register(MailboxViews.DETAIL, new MessageView(router, display, keyboard, model, progress, body, footer));
        router.register(MailboxViews.COMPOSE, new ComposeView(router, display, keyboard, progress, footer));
        router.background(client);
        router.start(MailboxViews.COVER);
        router.run();
    }

    /** Begins the SD card and reads Wi-Fi settings, the stored API key, and the polling interval from it. */
    private static MailboxSettings loadSettings(SdCard sd, MailboxKey key, MailboxPolling polling) {
        key.begin();
        polling.begin();
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
