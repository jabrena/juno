package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.net.tcp.Tcp;
import io.github.jabrena.juno.hardware.net.udp.Udp;
import io.github.jabrena.juno.hardware.Arduino;
import io.github.jabrena.juno.hardware.net.Wifi;

/** Joining a network, then a UDP exchange and a TCP exchange. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class})
public final class WifiUdpApi {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        Arduino.getWifi().begin("ci-ssid", "ci-password");

        if (Arduino.getWifi().status() == Wifi.STATUS_CONNECTED) {
            int[] address = new int[4];
            Arduino.getWifi().localIP(address);
            Serial.println(address[3]);
            Arduino.getWifi().gatewayIP(address);
            Serial.println(address[3]);
            int[] mac = new int[6];
            Arduino.getWifi().macAddress(mac);
            Serial.println(mac[5]);
            byte[] payload = new byte[8];
            Udp.listen(4210);
            Udp.broadcast(4210, payload, 4);
            Serial.println(Udp.receive(payload, 8, address));
            Udp.stop();
            int[] peer = {192, 168, 1, 10};
            if (Tcp.open(peer, 7)) {
                Serial.println(Tcp.write(payload, payload.length));
                Serial.println(Tcp.read(payload, 0, payload.length, 1000));
                Tcp.close();
            }
        }
    }
}
