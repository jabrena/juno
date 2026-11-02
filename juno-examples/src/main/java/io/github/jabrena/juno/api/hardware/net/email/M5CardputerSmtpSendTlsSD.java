package io.github.jabrena.juno.api.hardware.net.email;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.hardware.M5StackCardputer;
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
 * Sends one test message to the card's own mailbox through STARTTLS on port 587, with every credential read from
 * {@code application.properties} in the Cardputer's built-in microSD slot. The message goes from the mailbox to
 * the same mailbox, with a fixed subject and body, so the card needs no sender, recipient, subject or body entries.
 *
 * <p>After the upgrade the server's certificate chain is checked against Mozilla's root certificates bundled with the
 * program, including its dates. The board has no clock of its own, so the first send sets it over SNTP
 * ({@code pool.ntp.org}); {@code -13} means the clock could not be set, and {@code -12} that the TLS handshake failed,
 * for example because the certificate is not trusted.
 *
 * <p>The card needs {@code wifi.ssid}, {@code wifi.password}, {@code smtp.host}, {@code smtp.username} and
 * {@code smtp.password}. The port is fixed at 587. Prints the status over Serial at 115200 baud: {@code 0} means the
 * message was accepted. A runtime {@code String} holds 31 bytes, so keep the password within that length.
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml compile juno:upload \
 *   -Djuno.main=io.github.jabrena.juno.api.hardware.net.email.M5CardputerSmtpSendTlsSD -Djuno.board=arduino-nano-esp32
 * ./mvnw -f juno-examples/pom.xml juno:monitor -Djuno.board=arduino-nano-esp32 -Djuno.baudRate=115200
 * }</pre>
 */
@Board({M5StackCardputerBoard.class})
public final class M5CardputerSmtpSendTlsSD {
    private static final Wifi WIFI = M5StackCardputer.getWifi();
    private static final SdCard SD_CARD = M5StackCardputer.getSd();

    private static final int IMPLICIT_TLS_SMTP_PORT = 465;
    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    private M5CardputerSmtpSendTlsSD() {
    }

    public static void main(String[] args) throws IOException {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);

        if (!SD_CARD.begin()) {
            Serial.println("SD card initialization failed");
            return;
        }
        Account account = loadAccount();
        if (account == null) {
            return;
        }
        if (!connectWifi(account.ssid(), account.password())) {
            return;
        }

        Serial.println("Sending mail over implicit TLS");
        // Smtp.send takes all its String arguments as literals or all as runtime values, and the card's values are
        // runtime ones; a runtime String holds at most 31 bytes, so both stay short.
        String subject = new StringBuilder(32).append("juno STARTTLS test").toString();
        String body = new StringBuilder(32).append("Test message from juno").toString();
        int status = Smtp.sendTls(account.host(), IMPLICIT_TLS_SMTP_PORT, account.username(), account.smtpPassword(),
                account.username(), account.username(), subject, body);
        Serial.print("status=");
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

    private static Account loadAccount() throws IOException {
        try (InputStream file = SD_CARD.open("application.properties")) {
            if (file == null) {
                Serial.println("application.properties not found");
                return null;
            }
            Properties properties = new Properties();
            properties.load(file);
            String ssid = properties.getProperty("wifi.ssid");
            String password = properties.getProperty("wifi.password");
            String host = properties.getProperty("smtp.host");
            String username = properties.getProperty("smtp.username");
            String smtpPassword = properties.getProperty("smtp.password");
            if (ssid == null || password == null || host == null || username == null || smtpPassword == null) {
                Serial.println("a wifi.* or smtp.* entry is missing");
                return null;
            }
            return new Account(ssid, password, host, username, smtpPassword);
        }
    }

    private record Account(String ssid, String password, String host, String username, String smtpPassword) {
    }
}
