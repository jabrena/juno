package io.github.jabrena.juno.api.hardware.net.udp;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoUnoQBoard;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.api.hardware.security.ExampleSecrets;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

import java.time.LocalDateTime;

/**
 * Prints UTC from {@link LocalDateTime#now()} every ten seconds at 115200 baud. Joins Wi-Fi with
 * credentials from the ATECC608 {@code SecretStore}; the clock synchronizes over SNTP with the default
 * {@code pool.ntp.org} host and port 123.
 */
@Board({
    ArduinoUnoR4WiFiBoard.class,
    ArduinoUnoQBoard.class,
    ArduinoUnoR3WiFiRev2Board.class,
    ArduinoNanoEsp32Board.class,
    AiThinkerEsp32CamBoard.class,
    M5StackCardputerBoard.class,
    RaspberryPico2WifiBoard.class
})
public final class SntpTime {
    private static final Wifi WIFI = ArduinoUno.getWifi();

    private static final int MAX_CONNECTION_ATTEMPTS = 30;

    private SntpTime() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        WIFI.begin(ExampleSecrets.wifiSsid(), ExampleSecrets.wifiPassword());
        int attempts = 0;
        while (WIFI.status() != Wifi.STATUS_CONNECTED && attempts < MAX_CONNECTION_ATTEMPTS) {
            Delay.millis(1000);
            attempts = attempts + 1;
        }
        if (WIFI.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("wifi connection failed");
            return;
        }
        Serial.println("wifi connected");

        while (true) {
            LocalDateTime now = LocalDateTime.now();
            if (now.equals(LocalDateTime.MIN)) {
                Serial.println("sntp unavailable");
            } else {
                Serial.println(now.toString());
            }
            Delay.millis(10000);
        }
    }
}
