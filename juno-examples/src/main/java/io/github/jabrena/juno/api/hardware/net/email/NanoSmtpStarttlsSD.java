package io.github.jabrena.juno.api.hardware.net.email;

import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoNano;
import io.github.jabrena.juno.hardware.device.carrier.NanoConnectorCarrier;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.email.Smtp;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Joins the Wi-Fi network named in {@code application.properties} on the microSD card of a Nano Connector Carrier,
 * then sends one plain-text message through STARTTLS ({@link Smtp#send}, port 587), which upgrades the plain connection
 * to TLS after the server's greeting. The server, the credentials,
 * the sender, the recipient, the subject and the body all come from the card, so the program holds no secrets.
 * Prints the status code over Serial at 115200 baud: {@code 0} means the message was accepted.
 *
 * <p>The card needs {@code wifi.ssid}, {@code wifi.password}, {@code smtp.host}, {@code smtp.username},
 * {@code smtp.password}, {@code smtp.from}, {@code smtp.to}, {@code smtp.subject} and {@code smtp.body}. A runtime
 * {@code String} holds 31 bytes, so keep the password, the subject and the body within that length. Unlike
 * {@link Smtp#sendTls}, the STARTTLS wrapper ({@code ESP_SSLClient}) does not check the server's certificate chain yet.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.email.NanoSmtpStarttlsSD -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 */
@Board({ArduinoNanoEsp32Board.class})
public final class NanoSmtpStarttlsSD {
    private static final Wifi WIFI = ArduinoNano.getWifi();
    private static final SdCard SD_CARD = new NanoConnectorCarrier().getSdCard();

    private static final int STARTTLS_SMTP_PORT = 587;
    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    private NanoSmtpStarttlsSD() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        Mail mail = loadMail();
        if (mail == null) {
            return;
        }
        if (!connectWifi(mail.ssid(), mail.password())) {
            return;
        }

        Serial.println("Sending mail through STARTTLS");
        int status = Smtp.send(mail.host(), STARTTLS_SMTP_PORT, mail.username(), mail.smtpPassword(),
                mail.from(), mail.to(), mail.subject(), mail.body());
        Serial.print("smtp status=");
        Serial.println(status);
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

    private static Mail loadMail() throws IOException {
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
        String host = properties.getProperty("smtp.host");
        String username = properties.getProperty("smtp.username");
        String smtpPassword = properties.getProperty("smtp.password");
        String from = properties.getProperty("smtp.from");
        String to = properties.getProperty("smtp.to");
        String subject = properties.getProperty("smtp.subject");
        String body = properties.getProperty("smtp.body");
        if (ssid == null || password == null || host == null || username == null || smtpPassword == null
                || from == null || to == null || subject == null || body == null) {
            Serial.println("a wifi.* or smtp.* entry is missing");
            return null;
        }
        return new Mail(ssid, password, host, username, smtpPassword, from, to, subject, body);
    }

    private record Mail(String ssid, String password, String host, String username, String smtpPassword,
            String from, String to, String subject, String body) {
    }
}
