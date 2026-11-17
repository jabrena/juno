package io.github.jabrena.juno.api.hardware.net.email;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.email.Smtp;

/** Compile-only coverage for the Portenta's implicit-TLS and STARTTLS email transports. */
@Board(ArduinoPortentaH7Board.class)
public final class PortentaEmailCompile {
    private PortentaEmailCompile() {
    }

    public static void main(String[] args) {
        String host = MCU.getSecretStore().getString("smtp.host");
        String username = MCU.getSecretStore().getString("smtp.username");
        String password = MCU.getSecretStore().getString("smtp.password");
        String from = username;
        String to = username;
        String subject = "Juno Portenta probe";
        String body = "Transport compile probe";
        Smtp.sendTls(host, 465, username, password, from, to, subject, body);
        Smtp.send(host, 587, username, password, from, to, subject, body);
    }
}
