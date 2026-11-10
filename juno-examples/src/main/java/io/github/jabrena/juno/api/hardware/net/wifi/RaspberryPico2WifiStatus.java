package io.github.jabrena.juno.api.hardware.net.wifi;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.hardware.RaspberryPico2;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Joins the network named by the build-time {@code JUNO_WIFI_SSID}/{@code JUNO_WIFI_PASSWORD} with the Raspberry Pi
 * Pico 2 W's CYW43439 radio, then prints {@link Wifi#status} once a second and the station address once connected.
 */
@Board(RaspberryPico2WifiBoard.class)
public final class RaspberryPico2WifiStatus {
    private RaspberryPico2WifiStatus() {
    }

    /** Starts Wi-Fi and reports its status every second. */
    public static void main(String[] args) {
        Wifi wifi = RaspberryPico2.getWifi();
        int[] address = new int[4];
        Serial.begin(BaudRate.BAUD_9600);
        Delay.millis(2000);
        Serial.println("Starting WiFi");
        wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));
        while (true) {
            int status = wifi.status();
            Serial.print("WiFi status: ");
            Serial.println(status);
            if (status == Wifi.STATUS_CONNECTED) {
                wifi.localIP(address);
                Serial.print("IP: ");
                Serial.print(address[0]);
                Serial.print(".");
                Serial.print(address[1]);
                Serial.print(".");
                Serial.print(address[2]);
                Serial.print(".");
                Serial.println(address[3]);
            }
            Delay.millis(1000);
        }
    }
}
