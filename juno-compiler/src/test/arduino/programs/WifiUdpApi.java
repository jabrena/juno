package demo;

import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

/** Joining a network, then a UDP exchange and a TCP exchange. */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoUnoQBoard.class, ArduinoUnoR3WiFiRev2Board.class})
public final class WifiUdpApi {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);

        MCU.getWifi().begin("ci-ssid", "ci-password");

        if (MCU.getWifi().status() == Wifi.STATUS_CONNECTED) {
            int[] address = new int[4];
            MCU.getWifi().localIP(address);
            Serial.println(address[3]);
            MCU.getWifi().gatewayIP(address);
            Serial.println(address[3]);
            int[] mac = new int[6];
            MCU.getWifi().macAddress(mac);
            Serial.println(mac[5]);
            byte[] payload = new byte[8];
            try {
                DatagramSocket socket = new DatagramSocket(4210);
                InetAddress broadcast = InetAddress.getByAddress(new byte[] {(byte) 255, (byte) 255, (byte) 255, (byte) 255});
                socket.send(new DatagramPacket(payload, 4, broadcast, 4210));
                socket.setSoTimeout(10);
                socket.receive(new DatagramPacket(payload, 8));
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
        }
    }
}
