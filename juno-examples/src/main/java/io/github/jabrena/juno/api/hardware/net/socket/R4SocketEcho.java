package io.github.jabrena.juno.api.hardware.net.socket;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.hardware.net.wifi.WifiCredentials;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Small TCP socket probe that finds its echo server over UDP first, like {@code KasaSmartPlugDiscovery}: joins Wi-Fi with
 * then keeps one TCP connection open to that peer's port 7007 and every five seconds writes a line and reads the same
 * number of bytes back, reporting each exchange at 115200 baud. The connection is opened once, not per exchange, so the
 * loop allocates nothing.
 */
@Board(ArduinoUnoR4WiFiBoard.class)
public final class R4SocketEcho {
    private static final Wifi WIFI = ArduinoUno.getWifi();
    private static final SdCard SD_CARD = new TftTouchShield().getSdCard();

    private static final int DISCOVERY_PORT = 4210;
    private static final int ECHO_PORT = 7007;
    private static final int LINE_LENGTH = 11;
    private static final int POLL_MILLIS = 10;
    private static final int READ_TIMEOUT_MILLIS = 3000;
    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        if (!connectWifi()) {
            return;
        }
        try {
            echo(discoverPeer());
        } catch (IOException e) {
            Serial.println("tcp echo failed");
        }
    }

    private static InetAddress discoverPeer() throws IOException {
        DatagramSocket discovery = new DatagramSocket(DISCOVERY_PORT);
        discovery.setSoTimeout(POLL_MILLIS);
        InetAddress broadcast = InetAddress.getByAddress(new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255});
        byte[] question = {'j', 'u', 'n', 'o', '?'};
        DatagramPacket questionPacket = new DatagramPacket(question, question.length, broadcast, DISCOVERY_PORT);
        byte[] answer = new byte[16];
        DatagramPacket answerPacket = new DatagramPacket(answer, answer.length);
        while (true) {
            discovery.send(questionPacket);
            for (int wait = 0; wait < 100; wait++) {
                discovery.receive(answerPacket);
                if (answerPacket.getLength() > 0 && answer[0] != 'j') {
                    discovery.close();
                    Serial.println("peer found");
                    return answerPacket.getAddress();
                }
            }
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
        WifiCredentials credentials = WifiCredentials.load(SD_CARD);
        if (credentials == null) {
            return false;
        }
        WIFI.begin(credentials.ssid(), credentials.password());
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
