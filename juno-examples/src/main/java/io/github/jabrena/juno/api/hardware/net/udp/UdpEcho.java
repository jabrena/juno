package io.github.jabrena.juno.api.hardware.net.udp;

import io.github.jabrena.juno.hardware.device.wifi.ArduinoWifiShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.udp.Udp;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Small UDP probe: joins Wi-Fi with {@code JUNO_WIFI_SSID}/{@code JUNO_WIFI_PASSWORD}, listens on port 4210, broadcasts
 * a {@code juno} hello there every five seconds and echoes every packet it receives back to its sender, reporting both
 * at 115200 baud. Small enough for a UNO R3 with the Arduino WiFi Shield (build it with {@code -Djuno.Xmx=512}); from a
 * computer on the same network, {@code nc -u <board-ip> 4210} sends packets and shows the echoes.
 */
public final class UdpEcho {
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
        if (!Udp.listen(PORT)) {
            Serial.println("udp listen failed");
            return;
        }

        byte[] hello = {'j', 'u', 'n', 'o', '\n'};
        byte[] packet = new byte[64];
        int[] source = new int[Udp.ENDPOINT_SIZE];
        int sinceHello = HELLO_INTERVAL_MILLIS;
        while (true) {
            if (sinceHello >= HELLO_INTERVAL_MILLIS) {
                Serial.print("broadcast=");
                Serial.println(Udp.broadcast(PORT, hello, hello.length));
                sinceHello = 0;
            }
            int length = Udp.receive(packet, packet.length, source);
            if (length > 0) {
                Serial.print("received=");
                Serial.print(length);
                Serial.print(" from .");
                Serial.print(source[3]);
                Serial.print(":");
                Serial.print(source[Udp.PORT]);
                Serial.print(" echo=");
                Serial.println(Udp.send(source, source[Udp.PORT], packet, length));
            }
            Delay.millis(POLL_MILLIS);
            sinceHello = sinceHello + POLL_MILLIS;
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
