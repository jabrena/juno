package io.github.jabrena.juno.api.hardware.net.tcp;

import io.github.jabrena.juno.hardware.device.wifi.ArduinoWifiShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.tcp.Tcp;
import io.github.jabrena.juno.hardware.net.udp.Udp;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Small TCP probe that finds its echo server over UDP first, like {@code KasaSmartPlugDiscovery}: joins Wi-Fi with
 * {@code JUNO_WIFI_SSID}/{@code JUNO_WIFI_PASSWORD}, broadcasts {@code juno?} on UDP port 4210 until a peer answers,
 * then every five seconds opens a TCP connection to that peer's port 7007, writes a line and reads the same number of
 * bytes back, reporting each exchange at 115200 baud. Replies go to the known port rather than the packet's source
 * port, which the UNO R3's WiFi Shield picks at random. Small enough for a UNO R3 with the Arduino WiFi Shield (build it
 * with {@code -Djuno.Xmx=512}).
 */
@SuppressWarnings("java:S2189")
public final class TcpEcho {
    private static final Wifi WIFI = new ArduinoWifiShield().getWifi();

    private static final int DISCOVERY_PORT = 4210;
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

        int[] peer = new int[Udp.ENDPOINT_SIZE];
        if (!discoverPeer(peer)) {
            return;
        }
        Serial.print("peer=.");
        Serial.println(peer[3]);

        byte[] line = {'h', 'e', 'l', 'l', 'o', ' ', 'j', 'u', 'n', 'o', '\n'};
        byte[] reply = new byte[LINE_LENGTH];
        while (true) {
            if (Tcp.open(peer, ECHO_PORT)) {
                int written = Tcp.write(line, line.length);
                int read = Tcp.read(reply, 0, reply.length, READ_TIMEOUT_MILLIS);
                Tcp.close();
                Serial.print("tcp written=");
                Serial.print(written);
                Serial.print(" read=");
                Serial.print(read);
                Serial.print(" match=");
                Serial.println(sameBytes(line, reply) ? 1 : 0);
            } else {
                Serial.println("tcp open failed");
            }
            Delay.millis(5000);
        }
    }

    private static boolean discoverPeer(int[] peer) {
        if (!Udp.listen(DISCOVERY_PORT)) {
            Serial.println("udp listen failed");
            return false;
        }
        byte[] question = {'j', 'u', 'n', 'o', '?'};
        byte[] answer = new byte[16];
        while (true) {
            Udp.broadcast(DISCOVERY_PORT, question, question.length);
            for (int wait = 0; wait < 100; wait++) {
                if (Udp.receive(answer, answer.length, peer) > 0 && answer[0] != 'j') {
                    Udp.stop();
                    return true;
                }
                Delay.millis(10);
            }
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
