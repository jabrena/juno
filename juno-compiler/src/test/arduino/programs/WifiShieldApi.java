package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoR3;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.net.tcp.Tcp;
import io.github.jabrena.juno.hardware.net.udp.Udp;
import io.github.jabrena.juno.hardware.device.wifi.ArduinoWifiShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpClient;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Joining a network, a UDP exchange, a TCP exchange and a plain-HTTP request through the retired Arduino WiFi Shield on a UNO R3. */
@Board(ArduinoUnoR3.class)
public final class WifiShieldApi {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        Wifi wifi = new ArduinoWifiShield().getWifi();
        wifi.begin("ci-ssid", "ci-password");

        if (wifi.status() == Wifi.STATUS_CONNECTED) {
            int[] address = new int[4];
            wifi.localIP(address);
            Serial.println(address[3]);
            wifi.gatewayIP(address);
            Serial.println(address[3]);
            int[] mac = new int[6];
            wifi.macAddress(mac);
            Serial.println(mac[5]);
            byte[] payload = new byte[8];
            int[] source = new int[Udp.ENDPOINT_SIZE];
            Udp.listen(4210);
            Serial.println(Udp.broadcast(4210, payload, payload.length));
            Serial.println(Udp.receive(payload, payload.length, source));
            Udp.stop();
            int[] peer = {192, 168, 1, 10};
            if (Tcp.open(peer, 7)) {
                Serial.println(Tcp.write(payload, payload.length));
                Serial.println(Tcp.read(payload, 0, payload.length, 1000));
                Tcp.close();
            }
            byte[] headers = new byte[16];
            int[] status = new int[2];
            Serial.println(HttpClient.get("http://example.com/", payload, payload.length, headers, headers.length,
                    status));
        }
    }
}
