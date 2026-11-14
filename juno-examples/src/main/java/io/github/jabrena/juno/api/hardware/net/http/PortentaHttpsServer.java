package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.net.http.HttpServer;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/** Compile-time and runtime skeleton for the Portenta H7 TLS server transport. */
@Board(ArduinoPortentaH7Board.class)
public final class PortentaHttpsServer {
    private static final byte[] CERTIFICATE = new byte[2048];
    private static final byte[] KEY = new byte[2048];

    private PortentaHttpsServer() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        if (!HttpServer.beginTls(443, CERTIFICATE, 0, KEY, 0)) {
            Serial.println("Provide a PEM certificate and key before starting HTTPS");
            return;
        }
        Serial.println("Portenta HTTPS server listening");
    }
}
