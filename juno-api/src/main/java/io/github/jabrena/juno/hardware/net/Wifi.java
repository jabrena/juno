package io.github.jabrena.juno.hardware.net;

/**
 * Wi-Fi connection control. {@link io.github.jabrena.juno.hardware.ArduinoOne#getWifi()} is the board's own radio, which the
 * compiler lowers to the Arduino board networking stack ({@code WiFiS3} on UNO R4 WiFi, {@code WiFiNINA} on UNO WiFi
 * Rev2, {@code Arduino_RouterBridge} on UNO Q); the UNO R3 has no radio of its own, so it gets its Wi-Fi from
 * {@link io.github.jabrena.juno.hardware.device.wifi.ArduinoWifiShield#getWifi()} (the retired Arduino WiFi Shield's
 * legacy {@code WiFi} library) instead. A unit test passes {@code mock(Wifi.class)}. Get it once, at startup, and pass
 * it to the classes that need it.
 *
 * <p>{@code ssid}/{@code password} may be string literals, compile-time values resolved from
 * {@code System.getenv("NAME")}, or stable runtime strings returned by APIs such as
 * {@link java.util.Properties#getProperty(String)}. This allows a
 * deployed board to keep credentials on removable storage instead of baking them into firmware.
 */
public interface Wifi {

    /** Default name for Juno's open, local-development access point. */
    String DEFAULT_ACCESS_POINT_SSID = "JunoAP";

    /** An empty passphrase deliberately makes {@link #DEFAULT_ACCESS_POINT_SSID} an open network. */
    String DEFAULT_ACCESS_POINT_PASSWORD = "";

    /** The {@link #status} value once connected, mirroring Arduino's {@code WL_CONNECTED}. */
    int STATUS_CONNECTED = 3;

    /**
     * The {@link #status} value when the board gets no answer from its Wi-Fi hardware, mirroring Arduino's
     * {@code WL_NO_SHIELD}, e.g. a UNO R3 whose Arduino WiFi Shield is missing or not seated. With no shield at all the
     * legacy library may instead keep waiting on the shield's D7 handshake line.
     */
    int STATUS_NO_SHIELD = 255;

    /** The {@link #status} value once {@link #beginAP} is serving, mirroring {@code WL_AP_LISTENING} (UNO R4 WiFi only). */
    int STATUS_AP_LISTENING = 7;

    /**
     * Starts station-mode Wi-Fi on UNO R4 WiFi. On UNO Q, Wi-Fi belongs to Linux and must already
     * be configured through App Lab or {@code nmcli}; this method initializes the MCU-to-Linux
     * bridge and ignores the credential arguments.
     */
    void begin(String ssid, String password);

    /**
     * Starts the board as a Wi-Fi access point named {@code ssid}, so a phone or laptop can join it
     * directly (typically to serve a provisioning page through {@link
     * io.github.jabrena.juno.hardware.net.http.HttpServer}). On UNO R4 WiFi, {@code password} is a WPA2
     * passphrase of at least 8 characters, or empty for an open network; call {@link #localIP} after
     * {@link #status} reports {@link #STATUS_AP_LISTENING} to obtain the access point's address. On UNO Q the
     * MCU cannot create an access point: Linux owns Wi-Fi, so the hotspot must already be running
     * ({@code nmcli device wifi hotspot ssid <ssid> password <password>}); this method only
     * initializes the MCU-to-Linux bridge, ignores both arguments, and {@link #status} reports
     * {@link #STATUS_CONNECTED} once the bridge is up.
     */
    void beginAP(String ssid, String password);

    int status();

    /**
     * Writes the board's current IPv4 address into {@code octets} (caller-owned, at least 4
     * entries), one byte value (0-255) per element, most significant first — e.g. {@code 192, 168,
     * 1, 45}. When {@link #status} reports {@link #STATUS_AP_LISTENING}, this is the access point's
     * address; when it reports {@link #STATUS_CONNECTED}, it is the station address. UNO Q's
     * current router API does not expose either address and writes four zeroes. Juno has no
     * heap, so there is no {@code String}-returning form here — print each octet with {@link
     * io.github.jabrena.juno.hardware.serial.Serial#print(int)} and a literal {@code "."} between them.
     */
    void localIP(int[] octets);

    /**
     * Writes the IPv4 address of the router the board is connected to (its gateway) into {@code octets} (at least 4
     * entries), in the same form as {@link #localIP}. Only meaningful once {@link #status} reports {@link
     * #STATUS_CONNECTED}; a gateway that does not share the board's subnet usually means a bad DHCP lease. UNO Q's
     * router API does not expose it and writes four zeroes.
     */
    void gatewayIP(int[] octets);

    /**
     * Writes the Wi-Fi interface's six-byte hardware (MAC) address into {@code octets} (at least 6 entries), one byte
     * value (0-255) per element, most significant first; print them in hex to compare against a router's client
     * list. UNO Q's router API does not expose it and writes six zeroes.
     */
    void macAddress(int[] octets);
}
