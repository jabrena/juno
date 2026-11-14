package io.github.jabrena.juno.api.hardware.security;

import io.github.jabrena.juno.annotations.ArduinoPortentaH7Board;
import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.security.Atecc608;
import io.github.jabrena.juno.hardware.security.SecretSlots;
import io.github.jabrena.juno.hardware.security.SecretStore;
import io.github.jabrena.juno.hardware.serial.BaudRate;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.Delay;

/**
 * Joins Wi-Fi with the SSID and password stored in the ATECC608 on the UNO R4 WiFi's I2C bus, so no credential sits on the
 * SD card or in the firmware. Provision the values first with {@code juno:secret}, using the same name-to-slot table as
 * {@link SecretProvisioner}. Prints the Wi-Fi status once a second.
 *
 * <p>The board builds runtime {@code String}s of at most 31 characters, so the SSID and password must each fit in that.
 * A longer value is not truncated: the board may stop without a message.
 */
@Board({ArduinoUnoR4WiFiBoard.class, ArduinoPortentaH7Board.class})
public final class SecretWifiStatus {

    public static void main(String[] args) {
        Serial.begin(BaudRate.BAUD_115200);
        Delay.millis(2000);
        I2c i2c = MCU.getI2c();
        i2c.begin();
        SecretSlots slots = new SecretSlots().add("wifi.ssid", 8).add("wifi.password", 9);
        SecretStore secrets = new Atecc608(i2c, slots).getSecretStore();

        String ssid = secrets.getString("wifi.ssid");
        String password = secrets.getString("wifi.password");
        if (ssid == null || password == null) {
            Serial.println("Not possible to start WiFi: secrets are missing from the chip");
            return;
        }
        Wifi wifi = MCU.getWifi();
        wifi.begin(ssid, password);
        while (true) {
            Serial.println("WiFi status: " + wifi.status());
            Delay.millis(1000);
        }
    }
}
