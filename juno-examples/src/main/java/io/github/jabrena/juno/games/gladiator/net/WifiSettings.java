package io.github.jabrena.juno.games.gladiator.net;

import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The Wi-Fi network a gladiator board joins, read from {@code application.properties} on the TFT shield's microSD
 * card (its chip select is on D10):
 *
 * <pre>
 * wifi.ssid=MyNetwork
 * wifi.password=MyPassword
 * </pre>
 *
 * An UNO Q does not need it: it uses the connection configured on its Linux side ({@link #NONE}).
 */
public record WifiSettings(String ssid, String password) {
    public static final String FILE = "application.properties";
    /** The {@link #FILE} key naming the network. */
    public static final String SSID_KEY = "wifi.ssid";
    /** The {@link #FILE} key holding the network's password. */
    public static final String PASSWORD_KEY = "wifi.password";
    /** No network of its own to join, for a board whose connection is set up elsewhere. */
    public static final WifiSettings NONE = new WifiSettings("", "");

    /** Reads the network from {@code sdCard}; null, saying why over serial, when the card or either key is missing. */
    public static WifiSettings load(SdCard sdCard) {
        if (!sdCard.begin()) {
            Serial.println("No SD card");
            return null;
        }
        InputStream file = sdCard.open(FILE);
        if (file == null) {
            Serial.println("No application.properties");
            return null;
        }
        Properties properties = new Properties();
        try {
            properties.load(file);
            file.close();
        } catch (IOException e) {
            Serial.println("Unreadable settings file");
            return null;
        }
        String network = properties.getProperty(SSID_KEY);
        String secret = properties.getProperty(PASSWORD_KEY);
        if (network == null || secret == null) {
            Serial.println("Missing wifi.ssid or password");
            return null;
        }
        return new WifiSettings(network, secret);
    }
}
