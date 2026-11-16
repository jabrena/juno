package io.github.jabrena.juno.api.hardware.security;

import io.github.jabrena.juno.annotations.AiThinkerEsp32CamBoard;
import io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board;
import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR3WiFiRev2Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.annotations.M5StackCardputerBoard;
import io.github.jabrena.juno.annotations.RaspberryPico2WifiBoard;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.security.SecretStore;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Joins Wi-Fi with the SSID and password kept in the board's secret store, so no credential sits on the SD card or in
 * the firmware: {@link MCU#getSecretStore} returns the NVS store of the Nano ESP32, the Cardputer, the ESP32-CAM or the
 * UNO R4 WiFi's ESP32-S3 coprocessor, the Pico 2 W's TrustZone store, the Portenta H7's SE050C2 or the UNO WiFi Rev2's
 * EEPROM, and the secrets are asked for by name alone. Provision them first with {@code juno:secret}, which runs {@link SecretProvisioner}, once per
 * name:
 *
 * <pre>{@code
 * ./mvnw -f juno-examples/pom.xml juno:secret \
 *     -Djuno.main=io.github.jabrena.juno.api.hardware.security.SecretProvisioner \
 *     -Djuno.board=arduino-nano-esp32 -Djuno.secret.key=wifi.ssid
 * }</pre>
 *
 * <p>The result is printed every two seconds, so a monitor attached late still sees it. Each value must fit in 31
 * characters, the board's runtime {@code String} limit.
 */
@Board({ArduinoNanoEsp32Board.class, RaspberryPico2WifiBoard.class, M5StackCardputerBoard.class,
        AiThinkerEsp32CamBoard.class, ArduinoPortentaH7Board.class, ArduinoUnoR4WiFiBoard.class,
        ArduinoUnoR3WiFiRev2Board.class})
public final class NanoSecretWifi {
    private NanoSecretWifi() {
    }

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        // The board's store, like MCU.getWifi(); secrets are found by name, no slot numbers.
        SecretStore secrets = MCU.getSecretStore();

        String ssid = secrets.getString("wifi.ssid");
        String password = secrets.getString("wifi.password");
        if (ssid == null || password == null) {
            // No plain-text fallback: a missing secret is reported, never replaced.
            while (true) {
                Serial.println("Not possible to start WiFi: secrets are missing from the store");
                Delay.millis(2000);
            }
        }
        Wifi wifi = MCU.getWifi();
        wifi.begin(ssid, password);
        while (wifi.status() != Wifi.STATUS_CONNECTED) {
            Serial.println("WiFi status: " + wifi.status());
            Delay.millis(1000);
        }
        while (true) {
            Serial.println("WiFi connected");
            Delay.millis(2000);
        }
    }
}
