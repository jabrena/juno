package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.security.ExampleSecrets;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpMethod;
import io.github.jabrena.juno.hardware.net.http.HttpServer;
import io.github.jabrena.juno.hardware.net.http.HttpStatus;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * HTTPS on port 443 from the Portenta H7: {@code GET /status} answers {@code ok}, every other request answers 404.
 *
 * <p>The certificate and private key are not part of the firmware. The board prints {@code READY} until the host starts
 * sending, then reads two PEM blocks over USB serial: the certificate, a line {@code END}, the key and a line
 * {@code END}. Both stay in RAM, so a reboot needs them sent again. {@code scripts/portenta-https-send-pem.sh} sends them.
 */
@Board(ArduinoPortentaH7Board.class)
public final class PortentaHttpsServer {
    private static final Wifi WIFI = MCU.getWifi();

    private static final byte[] CERTIFICATE = new byte[2048];
    private static final byte[] KEY = new byte[2048];

    /** One PEM line is at most 64 characters; the capacity leaves room for longer lines and the {@code END} marker. */
    private static final int LINE_CAPACITY = 96;
    /** Idle polls between IP lines: 200 polls of 50 ms is about ten seconds. */
    private static final int IDLE_POLLS_PER_IP_LINE = 200;

    private PortentaHttpsServer() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        waitForPemHost();

        int certificateLength = receivePem(CERTIFICATE);
        int keyLength = receivePem(KEY);
        if (certificateLength < 0 || keyLength < 0) {
            Serial.println("PEM block too long or unreadable");
            return;
        }
        connectWifi();

        if (!HttpServer.beginTls(443, CERTIFICATE, certificateLength, KEY, keyLength)) {
            Serial.println("Certificate or key does not parse");
            return;
        }
        Serial.println("Portenta HTTPS server listening on port 443");

        byte[] body = new byte[128];
        int idlePolls = 0;
        while (true) {
            int bodyLength = HttpServer.accept(body, body.length);
            if (bodyLength >= 0) {
                route(HttpServer.method(), HttpServer.path());
            } else {
                idlePolls = idlePolls + 1;
                if (idlePolls >= IDLE_POLLS_PER_IP_LINE) {
                    printLocalIP();
                    idlePolls = 0;
                }
                Delay.millis(50);
            }
        }
    }

    /** Prints {@code READY} every half second until the first byte of the certificate arrives. */
    private static void waitForPemHost() {
        Serial.println("READY");
        while (Serial.available() == 0) {
            Delay.millis(500);
            Serial.println("READY");
        }
    }

    /**
     * Reads lines into {@code pem} until a line {@code END}. Returns the length of the PEM text, or -1 when the text does
     * not fit the buffer or a line is longer than {@link #LINE_CAPACITY}.
     */
    private static int receivePem(byte[] pem) {
        byte[] line = new byte[LINE_CAPACITY];
        int length = 0;
        while (true) {
            int lineLength = readLine(line);
            if (lineLength < 0) {
                return -1;
            }
            if (lineLength == 3 && line[0] == 'E' && line[1] == 'N' && line[2] == 'D') {
                return length;
            }
            if (length + lineLength + 1 > pem.length) {
                return -1;
            }
            for (int index = 0; index < lineLength; index++) {
                pem[length] = line[index];
                length = length + 1;
            }
            pem[length] = '\n';
            length = length + 1;
        }
    }

    /** Reads one line into {@code line} without its line ending; returns its length, or -1 when it is too long. */
    private static int readLine(byte[] line) {
        int length = 0;
        while (true) {
            int value = readByte();
            if (value == '\n') {
                return length;
            }
            if (value == '\r') {
                continue;
            }
            if (length >= line.length) {
                return -1;
            }
            line[length] = (byte) value;
            length = length + 1;
        }
    }

    private static int readByte() {
        while (Serial.available() == 0) {
            Delay.millis(5);
        }
        return Serial.read();
    }

    private static void route(String method, String path) {
        if (method.equals(HttpMethod.GET) && path.equals("/status")) {
            HttpServer.respond(HttpStatus.OK, "text/plain", "ok");
            Serial.println("served /status");
        } else {
            HttpServer.respond(HttpStatus.NOT_FOUND, "text/plain", "not found");
            Serial.println("served 404");
        }
    }

    private static void connectWifi() {
        WIFI.begin(ExampleSecrets.wifiSsid(), ExampleSecrets.wifiPassword());
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < 60) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("wifi connection failed");
            return;
        }
        Serial.println("wifi connected");
        printLocalIP();
    }

    private static void printLocalIP() {
        int[] ip = new int[4];
        WIFI.localIP(ip);
        Serial.print("ip=");
        Serial.print(ip[0]);
        Serial.print(".");
        Serial.print(ip[1]);
        Serial.print(".");
        Serial.print(ip[2]);
        Serial.print(".");
        Serial.println(ip[3]);
    }
}
