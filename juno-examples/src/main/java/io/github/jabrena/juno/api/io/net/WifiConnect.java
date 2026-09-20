package io.github.jabrena.juno.api.io.net;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.io.usb.BaudRate;
import io.github.jabrena.juno.api.io.usb.Serial;

/**
 * Connects to a WiFi network and reports the outcome over USB serial, readable with:
 * {@code arduino-cli monitor -p /dev/cu.usbmodemACA704344CE82 -c baudrate=9600}. Credentials come from
 * {@code JUNO_WIFI_SSID}/{@code JUNO_WIFI_PASSWORD} in Juno's own build-time environment (never written
 * to a file): {@code JUNO_WIFI_SSID=... JUNO_WIFI_PASSWORD=... java -jar
 * juno/target/juno-0.1.0-SNAPSHOT.jar compile --main io.github.jabrena.juno.api.io.net.WifiConnect
 * --classpath juno-examples/target/classes:juno/target/classes}.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class WifiConnect {
    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_9600);
        Delay.millis(2000);
        Serial.println("Connecting to WiFi...");
        Wifi.begin(System.getenv("JUNO_WIFI_SSID"), System.getenv("JUNO_WIFI_PASSWORD"));

        while (Wifi.status() != Wifi.STATUS_CONNECTED) {
            Serial.print("status=");
            Serial.println(Wifi.status());
            Delay.millis(1000);
        }

        while (true) {
            Serial.println("WiFi connected");
            Delay.millis(2000);
        }
    }
}
