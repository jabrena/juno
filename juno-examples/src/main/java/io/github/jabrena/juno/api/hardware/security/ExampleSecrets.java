package io.github.jabrena.juno.api.hardware.security;

import io.github.jabrena.juno.hardware.MCU;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.security.Atecc608;
import io.github.jabrena.juno.hardware.security.SecretSlots;
import io.github.jabrena.juno.hardware.security.SecretStore;
import io.github.jabrena.juno.hardware.serial.Serial;

/** Shared secure-element credentials used by the connected examples. The table matches {@link SecretProvisioner}. */
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

    private static final SecretStore STORE = open();

    private ExampleSecrets() {
    }

    /** Reads the Wi-Fi SSID from the secure element. */
    public static String wifiSsid() {
        return required(WIFI_SSID);
    }

    /** Reads the Wi-Fi password from the secure element. */
    public static String wifiPassword() {
        return required(WIFI_PASSWORD);
    }

    /** Reads the mailbox API key from the secure element. */
    public static String mailboxApiKey() {
        return required(MAILBOX_API_KEY);
    }

    /** Reads the UNO Q address from the secure element. */
    public static String mailboxQIp() {
        return required(MAILBOX_Q_IP);
    }

    /** Reads the SMTP host from the secure element. */
    public static String smtpHost() {
        return required(SMTP_HOST);
    }

    /** Reads the SMTP/POP3 account name from the secure element. */
    public static String smtpUsername() {
        return required(SMTP_USERNAME);
    }

    /** Reads the SMTP/POP3 password from the secure element. */
    public static String smtpPassword() {
        return required(SMTP_PASSWORD);
    }

    /** Reads the Supabase project host from the secure element. */
    public static String supabaseApiHost() {
        return required(SUPABASE_API_HOST);
    }

    /** Reads the Supabase publishable API key from the secure element. */
    public static String supabaseApiKey() {
        return required(SUPABASE_API_KEY);
    }

    /** Reads the Supabase table URL from the secure element. */
    public static String supabaseApiUrl() {
        return required(SUPABASE_API_URL);
    }

    private static String required(String name) {
        String value = STORE.getString(name);
        if (value == null) {
            Serial.println("Missing secure-element value: " + name);
            throw new IllegalStateException("Missing secure-element value: " + name);
        }
        return value;
    }

    private static SecretStore open() {
        I2c i2c = MCU.getI2c();
        i2c.begin();
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
        return new Atecc608(i2c, slots).getSecretStore();
    }
}
