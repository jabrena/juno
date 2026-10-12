package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQ;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.ArduinoUnoWiFiRev2;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.io.serial.BaudRate;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.net.Udp;
import io.github.jabrena.juno.api.net.Wifi;

/** Joining a network, then a UDP exchange. */
@Board({ArduinoUnoR4WiFi.class, ArduinoUnoQ.class, ArduinoUnoWiFiRev2.class})
public final class WifiUdpApi {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        Wifi.begin("ci-ssid", "ci-password");

        if (Wifi.status() == Wifi.STATUS_CONNECTED) {
            int[] address = new int[4];
            Wifi.localIP(address);
            Serial.println(address[3]);
            Wifi.gatewayIP(address);
            Serial.println(address[3]);
            int[] mac = new int[6];
            Wifi.macAddress(mac);
            Serial.println(mac[5]);
            byte[] payload = new byte[8];
            Udp.listen(4210);
            Udp.broadcast(4210, payload, 4);
            Serial.println(Udp.receive(payload, 8, address));
            Udp.stop();
        }
    }
}
