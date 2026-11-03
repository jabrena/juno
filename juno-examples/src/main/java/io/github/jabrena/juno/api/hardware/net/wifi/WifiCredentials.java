package io.github.jabrena.juno.api.hardware.net.wifi;

import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The Wi-Fi network an example joins, read from {@code application.properties} on the board's microSD card:
 *
 * <pre>
 * wifi.ssid=MyNetwork
 * wifi.password=MyPassword
 * </pre>
 */
public record WifiCredentials(String ssid, String password) {
    public static final String FILE = "application.properties";
    /** The {@link #FILE} key naming the network. */
    public static final String SSID_KEY = "wifi.ssid";
    /** The {@link #FILE} key holding the network's password. */
    public static final String PASSWORD_KEY = "wifi.password";

    /** Reads the network from {@code sdCard}; null, saying why over serial, when the card or either key is missing. */
    public static WifiCredentials load(SdCard sdCard) {
        if (!sdCard.begin()) {
            Serial.println("No SD card");
            return null;
        }
        try (InputStream file = sdCard.open(FILE)) {
            if (file == null) {
                Serial.println("No application.properties");
                return null;
            }
            Properties properties = new Properties();
            properties.load(file);
            String network = properties.getProperty(SSID_KEY);
            String secret = properties.getProperty(PASSWORD_KEY);
            if (network == null || secret == null) {
                Serial.println("Missing wifi.ssid or password");
                return null;
            }
            return new WifiCredentials(network, secret);
        } catch (IOException e) {
            Serial.println("Unreadable settings file");
            return null;
        }
    }
}
