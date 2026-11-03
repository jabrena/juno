package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoR3Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.device.wifi.ArduinoWifiShield;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.net.http.HttpClient;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

/** Joining a network, a UDP exchange, a TCP exchange and a plain-HTTP request through the retired Arduino WiFi Shield on a UNO R3. */
@Board(ArduinoUnoR3Board.class)
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
            try {
                DatagramSocket socket = new DatagramSocket(4210);
                InetAddress broadcast = InetAddress.getByAddress(new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255});
                socket.send(new DatagramPacket(payload, payload.length, broadcast, 4210));
                socket.setSoTimeout(10);
                socket.receive(new DatagramPacket(payload, payload.length));
                Serial.println(payload.length);
                socket.close();
                InetAddress peer = InetAddress.getByAddress(new byte[] {(byte) 192, (byte) 168, 1, 10});
                Socket stream = new Socket(peer, 7);
                stream.setSoTimeout(1000);
                stream.getOutputStream().write(payload, 0, payload.length);
                Serial.println(stream.getInputStream().read(payload, 0, payload.length));
                stream.close();
            } catch (IOException e) {
                Serial.println(0);
            }
            byte[] headers = new byte[16];
            int[] status = new int[2];
            Serial.println(HttpClient.get("http://example.com/", payload, payload.length, headers, headers.length,
                    status));
        }
    }
}
