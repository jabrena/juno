package io.github.jabrena.juno.api.hardware.security;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.device.sd.DataLoggerShield;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.security.SecretSlots;
import io.github.jabrena.juno.hardware.security.SecretStore;
import io.github.jabrena.juno.hardware.security.SecretStoreFactory;
import io.github.jabrena.juno.hardware.serial.Serial;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * The credentials the connected examples share. On a board with a secret store (the Portenta H7's SE050C2, an ESP32
 * board's NVS) they are read from it, with the same table as {@link SecretProvisioner}. Every other board reads the
 * same names from {@value #SETTINGS_FILE} on the SD card:
 *
 * <pre>
 * wifi.ssid=MyNetwork
 * wifi.password=MyPassword
 * </pre>
 *
 * <p>That card is the one {@link #useSdCard} was given, or else the card on chip select D10, where every UNO SD shield
 * Juno supports (TFT Touch, LCD Keypad, Data Logger) wires its socket. An example that already holds a shield passes
 * that shield's card, so the shield is not created twice. Each value is a runtime {@code String}, so it must fit in 31
 * characters.
 */
public final class ExampleSecrets {
    public static final String WIFI_SSID = "wifi.ssid";
    public static final String WIFI_PASSWORD = "wifi.password";
    public static final String MAILBOX_API_KEY = "mailbox.apikey";
    public static final String MAILBOX_Q_IP = "mailbox.q.ip";
    public static final String SMTP_HOST = "smtp.host";
    public static final String SMTP_USERNAME = "smtp.username";
    public static final String SMTP_PASSWORD = "smtp.password";
    public static final String SUPABASE_API_HOST = "supabase.api.host";
    public static final String SUPABASE_API_KEY = "supabase.api.key";
    public static final String SUPABASE_API_URL = "supabase.api.url";
    /** The file read on a board without a secret store. */
    public static final String SETTINGS_FILE = "application.properties";

    /** The names read from {@link #SETTINGS_FILE}; each one's value sits at the same index of {@link #SETTINGS}. */
    private static final String[] NAMES = {WIFI_SSID, WIFI_PASSWORD, MAILBOX_API_KEY, MAILBOX_Q_IP, SMTP_HOST,
        SMTP_USERNAME, SMTP_PASSWORD, SUPABASE_API_HOST, SUPABASE_API_KEY, SUPABASE_API_URL};
    private static final int NAME_COUNT = 10;
    private static final String[] SETTINGS = new String[NAME_COUNT];

    private static SdCard sdCard;
    private static SecretStore store;
    private static boolean settingsRead;

    private ExampleSecrets() {
    }

    /**
     * Reads the settings of a board without a secret store from {@code card} instead of the default D10 card, for an
     * example that already uses the card itself. The card is read as it is: it is not started here, because starting
     * it again would close the files the example has open. Call this once, before the first value is read. A board
     * with a secret store ignores it.
     */
    public static void useSdCard(SdCard card) {
        sdCard = card;
    }

    /** Reads the Wi-Fi SSID. */
    public static String wifiSsid() {
        return required(WIFI_SSID);
    }

    /** Reads the Wi-Fi password. */
    public static String wifiPassword() {
        return required(WIFI_PASSWORD);
    }

    /** Reads the mailbox API key. */
    public static String mailboxApiKey() {
        return required(MAILBOX_API_KEY);
    }

    /** Reads the UNO Q address. */
    public static String mailboxQIp() {
        return required(MAILBOX_Q_IP);
    }

    /** Reads the SMTP host. */
    public static String smtpHost() {
        return required(SMTP_HOST);
    }

    /** Reads the SMTP/POP3 account name. */
    public static String smtpUsername() {
        return required(SMTP_USERNAME);
    }

    /** Reads the SMTP/POP3 password. */
    public static String smtpPassword() {
        return required(SMTP_PASSWORD);
    }

    /** Reads the Supabase project host. */
    public static String supabaseApiHost() {
        return required(SUPABASE_API_HOST);
    }

    /** Reads the Supabase publishable API key. */
    public static String supabaseApiKey() {
        return required(SUPABASE_API_KEY);
    }

    /** Reads the Supabase table URL. */
    public static String supabaseApiUrl() {
        return required(SUPABASE_API_URL);
    }

    /**
     * The value of {@code name}, from the board's secret store or its SD card. The board switch sits here, not behind a
     * call, so the linker decides it for the board and only one source reaches the program: the SD-card code never
     * reaches a store board, and the store never reaches the others.
     */
    private static String required(String name) {
        String value = switch (MCU.getBoardModel()) {
            case PORTENTA_H7, NANO_ESP32, M5_CARDPUTER, AI_THINKER_ESP32_CAM -> store().getString(name);
            default -> setting(name);
        };
        if (value == null) {
            Serial.println("Missing setting: " + name);
            throw new IllegalStateException("Missing setting: " + name);
        }
        return value;
    }

    private static SecretStore store() {
        SecretStore opened = store;
        if (opened == null) {
            SecretSlots slots = new SecretSlots()
                    .add(WIFI_SSID, 8)
                    .add(WIFI_PASSWORD, 9)
                    .add(MAILBOX_API_KEY, 10)
                    .add(MAILBOX_Q_IP, 0)
                    .add(SMTP_HOST, 11)
                    .add(SMTP_USERNAME, 12)
                    .add(SMTP_PASSWORD, 13)
                    .add(SUPABASE_API_HOST, 14)
                    .add(SUPABASE_API_KEY, 15)
                    .add(SUPABASE_API_URL, 1);
            opened = SecretStoreFactory.open(slots);
            store = opened;
        }
        return opened;
    }

    private static String setting(String name) {
        if (!settingsRead) {
            settingsRead = true;
            load();
        }
        for (int i = 0; i < NAME_COUNT; i++) {
            if (NAMES[i].equals(name)) {
                return SETTINGS[i];
            }
        }
        return null;
    }

    /**
     * Reads {@value #SETTINGS_FILE} once into {@link #SETTINGS}, saying why over serial when the card or the file is
     * missing. The {@code Properties} stays a local, as Juno requires.
     */
    private static void load() {
        SdCard card = sdCard;
        if (card == null) {
            card = new DataLoggerShield().getSdCard();
            if (!card.begin()) {
                Serial.println("No SD card on D10");
                return;
            }
        }
        try (InputStream file = card.open(SETTINGS_FILE)) {
            if (file == null) {
                Serial.println("No " + SETTINGS_FILE);
                return;
            }
            Properties properties = new Properties();
            properties.load(file);
            for (int i = 0; i < NAME_COUNT; i++) {
                SETTINGS[i] = properties.getProperty(NAMES[i]);
            }
        } catch (IOException e) {
            Serial.println("Unreadable " + SETTINGS_FILE);
        }
    }
}
