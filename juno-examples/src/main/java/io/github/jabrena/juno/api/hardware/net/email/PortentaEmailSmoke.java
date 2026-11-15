package io.github.jabrena.juno.api.hardware.net.email;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.security.ExampleSecrets;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.email.Pop3Client;
import io.github.jabrena.juno.hardware.net.email.Smtp;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Runs the Portenta's implicit-TLS email transports once against the account in the secure store: a send on port 465
 * and a POP3 message count on port 995. The message goes from the account to itself. Prints each result: {@code 0}
 * means the send was accepted, a negative code names the failing step, and the POP3 line prints the message count.
 * STARTTLS on port 587 is not run here: {@code Smtp.send} needs {@code ESP_SSLClient}, which the Mbed core does not link.
 */
@Board(ArduinoPortentaH7Board.class)
public final class PortentaEmailSmoke {
    private static final Wifi WIFI = MCU.getWifi();

    private static final int IMPLICIT_TLS_SMTP_PORT = 465;
    private static final int IMPLICIT_TLS_POP3_PORT = 995;
    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    private PortentaEmailSmoke() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        WIFI.begin(ExampleSecrets.wifiSsid(), ExampleSecrets.wifiPassword());
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("wifi connection failed");
            return;
        }
        Serial.println("wifi connected");

        String host = ExampleSecrets.smtpHost();
        String username = ExampleSecrets.smtpUsername();
        String password = ExampleSecrets.smtpPassword();

        Serial.println("implicit tls send 465");
        int implicitTls = Smtp.sendTls(host, IMPLICIT_TLS_SMTP_PORT, username, password, username, username,
                "Juno Portenta TLS", "Sent by the Portenta H7 over implicit TLS on port 465.");
        Serial.print("tls result=");
        Serial.println(implicitTls);

        int count = Pop3Client.messageCount(host, IMPLICIT_TLS_POP3_PORT, username, password);
        Serial.print("pop3 count=");
        Serial.println(count);
        Serial.println("email done");
    }
}
