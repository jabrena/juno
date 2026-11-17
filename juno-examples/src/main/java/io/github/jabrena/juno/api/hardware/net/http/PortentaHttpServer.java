package io.github.jabrena.juno.api.hardware.net.http;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpMethod;
import io.github.jabrena.juno.hardware.net.http.HttpServer;
import io.github.jabrena.juno.hardware.net.http.HttpStatus;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Plain HTTP on port 80 from the Portenta H7: {@code GET /status} answers {@code ok}, every other request answers 404.
 * The board prints its IP address every ten seconds, so a serial monitor that attaches late still learns where to connect.
 */
@Board(ArduinoPortentaH7Board.class)
public final class PortentaHttpServer {
    private static final Wifi WIFI = MCU.getWifi();

    /** Idle polls between IP lines: 200 polls of 50 ms is about ten seconds. */
    private static final int IDLE_POLLS_PER_IP_LINE = 200;

    private PortentaHttpServer() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        connectWifi();

        HttpServer.begin(80);
        Serial.println("Listening on port 80");

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
        WIFI.begin(MCU.getSecretStore().getString("wifi.ssid"), MCU.getSecretStore().getString("wifi.password"));
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
