package io.github.jabrena.juno.api.hardware.net.email;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.email.Pop3Client;
import io.github.jabrena.juno.hardware.net.email.Smtp;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Runs each Portenta email transport once against the account in the secure store: a STARTTLS send on port 587, an
 * implicit-TLS send on port 465, and a POP3 message count on port 995. The messages go from the account to itself.
 * Prints each result: {@code 0} means the send was accepted, a negative code names the failing step, and the POP3 line
 * prints the message count.
 */
@Board(ArduinoPortentaH7Board.class)
public final class PortentaEmailSmoke {
    private static final Wifi WIFI = MCU.getWifi();

    private static final int STARTTLS_SMTP_PORT = 587;
    private static final int IMPLICIT_TLS_SMTP_PORT = 465;
    private static final int IMPLICIT_TLS_POP3_PORT = 995;
    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    private PortentaEmailSmoke() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        // The program runs once, so pause before the sends: a serial monitor opened after the upload then sees every result.
        Delay.millis(20000);
        WIFI.begin(MCU.getSecretStore().getString("wifi.ssid"), MCU.getSecretStore().getString("wifi.password"));
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

        String host = MCU.getSecretStore().getString("smtp.host");
        String username = MCU.getSecretStore().getString("smtp.username");
        String password = MCU.getSecretStore().getString("smtp.password");

        Serial.println("starttls send 587");
        int starttls = Smtp.send(host, STARTTLS_SMTP_PORT, username, password, username, username,
                "Juno Portenta STARTTLS", "Sent by the Portenta H7 over STARTTLS on port 587.");
        Serial.print("starttls result=");
        Serial.println(starttls);

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
