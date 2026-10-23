package io.github.jabrena.juno.api.hardware.net.wifi;

import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.M5StackCardputer;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/** Starts a Wi-Fi access point on the Cardputer and reports its status over USB serial. */
@Board(io.github.jabrena.juno.annotations.M5StackCardputer.class)
public final class M5CardputerWifi {
    private M5CardputerWifi() {
    }

    /** Starts the USB serial port and the Cardputer's ESP32-S3 Wi-Fi access point. */
    public static void main(String[] args) {
        Serial.begin(9600);
        Delay.millis(1000);
        Wifi wifi = M5StackCardputer.getWifi();
        wifi.beginAP("JunoCardputer", "juno1234");
        Serial.println("Wi-Fi access point started");
        Serial.println(wifi.status());
    }
}
