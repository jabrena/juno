package io.github.jabrena.juno.usecases.mailbox.server;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.carrier.PortentaHatCarrier;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.device.tft.Mhs35TftDisplay;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.security.SecretStore;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.usecases.mailbox.server.commons.MailboxStartupState;
import io.github.jabrena.juno.usecases.mailbox.server.commons.MailboxUiState;
import io.github.jabrena.juno.usecases.mailbox.server.cover.MailboxView;
import io.github.jabrena.juno.usecases.mailbox.server.security.MailboxSecurity;
import io.github.jabrena.juno.usecases.mailbox.server.services.MailboxController;
import io.github.jabrena.juno.usecases.mailbox.server.services.MailboxModel;
import io.github.jabrena.juno.usecases.mailbox.server.services.MailboxServerTask;
import io.github.jabrena.juno.usecases.mailbox.server.services.MailboxStartupTask;
import io.github.jabrena.juno.util.Delay;

/**
 * The mailbox composition root on an Arduino Portenta H7 in a Portenta Hat Carrier with the MHS-3.5 TFT touch HAT:
 * wires the SD-backed model, HTTPS controller and TFT view, joins Wi-Fi, then runs the UI and HTTPS tasks together.
 *
 * <p>The Wi-Fi network comes from the Portenta's secret store ({@code wifi.ssid} and {@code wifi.password}, written
 * once with {@code SecretProvisioner}), and the address the board gets is shown on the TFT and printed over Serial. The
 * TLS certificate and private key are read at startup from {@code /server.crt} and {@code /server.key} (PEM) on the
 * carrier's microSD card, so the firmware image holds no key and a certificate is replaced without rebuilding (see
 * {@link io.github.jabrena.juno.usecases.mailbox.server.services.MailboxCredentials}); the certificate must name the
 * board's address in its {@code subjectAltName}, so give the board a fixed DHCP lease.
 *
 * <p>The mailbox is available at {@code /api/v1/mailbox/}, and {@code GET /actuator/info} identifies the service.
 * Every mailbox request must carry the API key in a {@code Mailbox-API-Key} header, or it is answered {@code 401}; the
 * key starts as {@code 1234567890}, is changed from the Security screen (the cover's gear, then SECURITY) and is kept
 * in {@code /api-key.txt} on the SD card. The board serves HTTPS on port 443, so the API key is protected in transit.
 * Messages are stored as {@code /mailbox/YYYYMMDDHHMMSS/<uuid>.txt}; files outside that timestamp-folder layout are
 * ignored.
 * Only the small index is kept in the board arena.
 *
 * <p>The screens are laid out for 240x320 portrait, which the 320x480 panel scales up by 4/3.
 * Copy {@code server.crt} and {@code server.key} to the SD card's root, then build it, for example:
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:flash \
 *   -Djuno.main=io.github.jabrena.juno.usecases.mailbox.server.Mailbox -Djuno.board=arduino-portenta-h7
 * }</pre>
 */
@Board(ArduinoPortentaH7Board.class)
public final class Mailbox {
    private static final int TOUCH_POLL_MILLIS = 100;
    private static final int WIFI_ATTEMPTS = 60;
    private static final int WIFI_RETRY_MILLIS = 1000;

    private Mailbox() {
    }

    public static void main(String[] args) {
        PortentaHatCarrier carrier = new PortentaHatCarrier();
        Mhs35TftDisplay tft = new Mhs35TftDisplay(carrier);
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        tft.setRotation(Mhs35TftDisplay.PORTRAIT);
        tft.fitLogicalSize(MailboxView.SCREEN_WIDTH, MailboxView.SCREEN_HEIGHT);

        SdCard sdCard = carrier.getSdCard();
        MailboxModel model = new MailboxModel(sdCard);
        MailboxSecurity security = new MailboxSecurity(sdCard);
        MailboxView view = new MailboxView(security);
        view.drawCover(tft, false, 0);
        view.drawInternetConnecting(tft);
        if (joinWifi(MCU.getWifi())) {
            int[] address = new int[4];
            MCU.getWifi().localIP(address);
            view.drawIp(tft, address);
            printLocalIP(address);
        } else {
            view.drawInternetOffline(tft);
            Serial.println("Mailbox stopped: Wi-Fi did not connect");
            return;
        }

        MailboxUiState uiState = new MailboxUiState();
        MailboxStartupState startupState = new MailboxStartupState();
        MailboxController controller = new MailboxController(model, security, uiState);
        byte[] requestBody = new byte[MailboxModel.MAX_REQUEST_BYTES];
        Clock clock = MCU.getClock();
        MailboxServerTask serverTask = new MailboxServerTask(controller, requestBody, clock);
        Thread.startVirtualThread(new MailboxStartupTask(sdCard, model, security, startupState, uiState, serverTask));
        runUserInterface(tft, view, uiState, startupState, clock);
    }

    /** The main task owns all TFT operations; a tap counts once, when the press starts. */
    private static void runUserInterface(Mhs35TftDisplay tft, MailboxView view, MailboxUiState uiState, MailboxStartupState startupState,
                                         Clock clock) {
        int nextTouchPoll = clock.millis();
        int renderedRevision = uiState.revision();
        int renderedStartupStatus = MailboxStartupState.STARTING;
        boolean touched = false;
        while (true) {
            int now = clock.millis();
            if (now - nextTouchPoll >= 0) {
                nextTouchPoll = now + TOUCH_POLL_MILLIS;
                boolean pressed = tft.readTouch();
                if (pressed && !touched) {
                    Serial.print("Touch ");
                    Serial.print(tft.touchX());
                    Serial.print(",");
                    Serial.println(tft.touchY());
                    view.touch(tft, tft.touchX(), tft.touchY());
                }
                touched = pressed;
                int startupStatus = startupState.status();
                if (startupStatus != renderedStartupStatus) {
                    if (startupStatus == MailboxStartupState.READY) {
                        view.updateStorageReady(tft, true);
                        view.drawInternetOnline(tft);
                    } else if (startupStatus == MailboxStartupState.FAILED) {
                        view.drawInternetOffline(tft);
                    }
                    renderedStartupStatus = startupStatus;
                }
                int revision = uiState.revision();
                if (revision != renderedRevision) {
                    view.drawMessageCount(tft, uiState.messageCount());
                    renderedRevision = revision;
                }
            }
            Delay.millis(1);
        }
    }

    /** Joins the network named in the secret store, waiting up to a minute; returns whether the board is on it. */
    private static boolean joinWifi(Wifi wifi) {
        SecretStore secrets = MCU.getSecretStore();
        wifi.begin(secrets.getString("wifi.ssid"), secrets.getString("wifi.password"));
        for (int attempt = 0; attempt < WIFI_ATTEMPTS && wifi.status() != Wifi.STATUS_CONNECTED; attempt++) {
            Delay.millis(WIFI_RETRY_MILLIS);
        }
        return wifi.status() == Wifi.STATUS_CONNECTED;
    }

    private static void printLocalIP(int[] octets) {
        Serial.print("IP: ");
        for (int index = 0; index < octets.length; index++) {
            if (index > 0) {
                Serial.print(".");
            }
            Serial.print(octets[index]);
        }
        Serial.println("");
    }
}
