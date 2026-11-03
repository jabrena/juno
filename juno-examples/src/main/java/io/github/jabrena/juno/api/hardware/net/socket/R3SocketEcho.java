package io.github.jabrena.juno.api.hardware.net.socket;

import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.device.wifi.ArduinoWifiShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Small TCP socket probe for the UNO R3 with the Arduino WiFi Shield. It joins Wi-Fi with the build-time
 * {@code JUNO_WIFI_SSID}/{@code JUNO_WIFI_PASSWORD}, then keeps one TCP connection open to the echo server at
 * {@link #PEER_ADDRESS} on port 7007 and every five seconds writes a line and reads the same number of bytes back,
 * reporting each exchange at 115200 baud. The peer's address is a constant, not discovered over UDP, because the
 * board's RAM cannot hold the discovery code. Change {@link #PEER_ADDRESS} to the echo server's address before building.
 */
@Board(ArduinoUnoR3Board.class)
public final class R3SocketEcho {
    private static final Wifi WIFI = new ArduinoWifiShield().getWifi();

    /** The echo server's IPv4 address: the desktop running the peer. */
    private static final byte[] PEER_ADDRESS = {(byte) 192, (byte) 168, 1, 42};
    private static final int ECHO_PORT = 7007;
    private static final int LINE_LENGTH = 11;
    private static final int READ_TIMEOUT_MILLIS = 3000;
    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        if (!connectWifi()) {
            return;
        }
        try {
            echo(InetAddress.getByAddress(PEER_ADDRESS));
        } catch (IOException e) {
            Serial.println("tcp echo failed");
        }
    }

    /** Keeps one connection to the peer and exchanges a line with it every five seconds. */
    private static void echo(InetAddress peer) throws IOException {
        byte[] line = {'h', 'e', 'l', 'l', 'o', ' ', 'j', 'u', 'n', 'o', '\n'};
        byte[] reply = new byte[LINE_LENGTH];
        Socket socket = new Socket(peer, ECHO_PORT);
        socket.setSoTimeout(READ_TIMEOUT_MILLIS);
        while (true) {
            Serial.print("tcp exchange match=");
            Serial.println(exchange(socket, line, reply) ? 1 : 0);
            Delay.millis(5000);
        }
    }

    /** Writes {@code line} and reads the echo back into {@code reply}; a failed exchange reports {@code false}. */
    private static boolean exchange(Socket socket, byte[] line, byte[] reply) {
        try {
            socket.getOutputStream().write(line, 0, line.length);
            socket.getInputStream().read(reply, 0, reply.length);
            return sameBytes(line, reply);
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean sameBytes(byte[] expected, byte[] actual) {
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                return false;
            }
        }
        return true;
    }

    private static boolean connectWifi() {
        WIFI.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("wifi connection failed");
            return false;
        }
        Serial.println("wifi connected");
        return true;
    }
}
