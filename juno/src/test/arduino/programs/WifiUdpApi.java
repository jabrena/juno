package demo;

import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Udp;
import io.github.jabrena.juno.api.net.Wifi;

/** Joining a network, then a UDP exchange. */
public final class WifiUdpApi {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        Wifi.begin("ci-ssid", "ci-password");

        if (Wifi.status() == Wifi.STATUS_CONNECTED) {
            int[] address = new int[4];
            Wifi.localIP(address);
            Serial.println(address[3]);
            byte[] payload = new byte[8];
            Udp.listen(4210);
            Udp.broadcast(4210, payload, 4);
            Serial.println(Udp.receive(payload, 8, address));
            Udp.stop();
        }
    }
}
