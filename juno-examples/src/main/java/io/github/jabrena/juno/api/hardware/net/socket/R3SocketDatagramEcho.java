package io.github.jabrena.juno.api.hardware.net.socket;

import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.device.wifi.ArduinoWifiShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * a {@code juno} hello there every five seconds and echoes every packet it receives back to its sender, reporting both
 * at 115200 baud. Small enough for a UNO R3 with the Arduino WiFi Shield (build it with {@code -Djuno.Xmx=512}); from a
 * computer on the same network, {@code nc -u <board-ip> 4210} sends packets and shows the echoes.
 */
@Board(ArduinoUnoR3Board.class)
public final class R3SocketDatagramEcho {
    private static final Wifi WIFI = new ArduinoWifiShield().getWifi();

    private static final int PORT = 4210;
    private static final int HELLO_INTERVAL_MILLIS = 5000;
    private static final int POLL_MILLIS = 10;
    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        if (!connectWifi()) {
            return;
        }
        try {
            echo();
        } catch (IOException e) {
            Serial.println("udp listen failed");
        }
    }

    /** Broadcasts a hello on a timer and echoes each datagram; each receive waits at most one poll interval. */
    private static void echo() throws IOException {
        DatagramSocket socket = new DatagramSocket(PORT);
        socket.setSoTimeout(POLL_MILLIS);
        InetAddress broadcast = InetAddress.getByAddress(new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255});
        byte[] hello = {'j', 'u', 'n', 'o', '\n'};
        DatagramPacket helloPacket = new DatagramPacket(hello, hello.length, broadcast, PORT);
        byte[] packet = new byte[64];
        DatagramPacket incoming = new DatagramPacket(packet, packet.length);
        int sinceHello = HELLO_INTERVAL_MILLIS;
        while (true) {
            if (sinceHello >= HELLO_INTERVAL_MILLIS) {
                Serial.println(send(socket, helloPacket) ? "broadcast=ok" : "broadcast=failed");
                sinceHello = 0;
            }
            socket.receive(incoming);
            if (incoming.getLength() > 0) {
                Serial.print("received=");
                Serial.print(incoming.getLength());
                Serial.print(" from port ");
                Serial.print(incoming.getPort());
                Serial.print(" echo=");
                Serial.println(send(socket, incoming) ? "ok" : "failed");
            }
            sinceHello = sinceHello + POLL_MILLIS;
        }
    }

    /** Sends one datagram, reporting a failed send instead of ending the echo loop. */
    private static boolean send(DatagramSocket socket, DatagramPacket packet) {
        try {
            socket.send(packet);
            return true;
        } catch (IOException e) {
            return false;
        }
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
        int[] address = new int[4];
        WIFI.localIP(address);
        Serial.print("wifi connected ip=");
        for (int i = 0; i < address.length; i++) {
            if (i > 0) {
                Serial.print(".");
            }
            Serial.print(address[i]);
        }
        Serial.println("");
        return true;
    }
}
