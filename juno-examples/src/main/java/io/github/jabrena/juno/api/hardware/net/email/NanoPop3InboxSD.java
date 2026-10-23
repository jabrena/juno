package io.github.jabrena.juno.api.hardware.net.email;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.email.Pop3Client;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Joins the Wi-Fi network named in {@code application.properties} on the microSD card of a Nano Connector Carrier,
 * then asks the mailbox over implicit TLS ({@link Pop3Client#messageCount}, port 995) for its message count every
 * minute. The server and the credentials come from the card. Prints the count, or the failing status, at 115200 baud.
 *
 * <p>The card needs {@code wifi.ssid}, {@code wifi.password}, {@code pop3.host}, {@code pop3.username} and
 * {@code pop3.password}. A runtime {@code String} holds 31 bytes, so keep the password within that length.
 * The TLS client checks the server's chain against Mozilla's root certificates.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.email.NanoPop3InboxSD -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoPop3InboxSD {
    private static final Wifi WIFI = ArduinoNano.getWifi();
    private static final SdCard SD_CARD = new NanoConnectorCarrier().getSdCard();

    private static final int IMPLICIT_TLS_POP3_PORT = 995;
    private static final int MAX_CONNECTION_ATTEMPTS = 30;
    private static final int CHECK_INTERVAL_MS = 60000;

    private NanoPop3InboxSD() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Mailbox mailbox = loadMailbox();
        if (mailbox == null) {
            return;
        }
        if (!connectWifi(mailbox.ssid(), mailbox.password())) {
            return;
        }

        while (true) {
            int count = Pop3Client.messageCount(mailbox.host(), IMPLICIT_TLS_POP3_PORT, mailbox.username(),
                    mailbox.pop3Password());
            if (count >= 0) {
                Serial.print("messages=");
                Serial.println(count);
            } else {
                Serial.print("pop3 status=");
                Serial.println(count);
            }
            Delay.millis(CHECK_INTERVAL_MS);
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

    private static Mailbox loadMailbox() throws IOException {
        if (!SD_CARD.begin()) {
            Serial.println("SD card initialization failed");
            return null;
        }
        InputStream file = SD_CARD.open("application.properties");
        if (file == null) {
            Serial.println("application.properties not found");
            return null;
        }
        Properties properties = new Properties();
        properties.load(file);
        file.close();
        String ssid = properties.getProperty("wifi.ssid");
        String password = properties.getProperty("wifi.password");
        String host = properties.getProperty("pop3.host");
        String username = properties.getProperty("pop3.username");
        String pop3Password = properties.getProperty("pop3.password");
        if (ssid == null || password == null || host == null || username == null || pop3Password == null) {
            Serial.println("a wifi.* or pop3.* entry is missing");
            return null;
        }
        return new Mailbox(ssid, password, host, username, pop3Password);
    }

    private record Mailbox(String ssid, String password, String host, String username, String pop3Password) {
    }
}
