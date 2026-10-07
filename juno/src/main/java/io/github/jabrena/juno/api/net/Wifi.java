package io.github.jabrena.juno.api.net;

/**
 * WiFi connection control recognized as compiler intrinsics by Juno, backed by the Arduino
 * board networking stack ({@code WiFiS3} on UNO R4 WiFi, {@code Arduino_RouterBridge} on UNO Q).
 *
 * <p>{@code ssid}/{@code password} may be string literals, compile-time values resolved from
 * {@code System.getenv("NAME")}, or stable runtime strings returned by APIs such as
 * {@link java.util.Properties#getProperty(String)}. This allows a
 * deployed board to keep credentials on removable storage instead of baking them into firmware.
 */
public final class Wifi {
    private Wifi() {
    }

    /** The {@link #status} value once connected, mirroring Arduino's {@code WL_CONNECTED}. */
    public static final int STATUS_CONNECTED = 3;

    /** The {@link #status} value once {@link #beginAP} is serving, mirroring {@code WL_AP_LISTENING} (UNO R4 WiFi only). */
    public static final int STATUS_AP_LISTENING = 7;

    /**
     * Starts station-mode Wi-Fi on UNO R4 WiFi. On UNO Q, Wi-Fi belongs to Linux and must already
     * be configured through App Lab or {@code nmcli}; this method initializes the MCU-to-Linux
     * bridge and ignores the credential arguments.
     */
    public static native void begin(String ssid, String password);

    /**
     * Starts the board as a Wi-Fi access point named {@code ssid}, so a phone or laptop can join it
     * directly (typically to serve a provisioning page through {@link
     * io.github.jabrena.juno.api.net.http.HttpServer}). On UNO R4 WiFi, {@code password} is a WPA2
     * passphrase of at least 8 characters, or empty for an open network; the board is reachable at
     * {@code 192.168.4.1} once {@link #status} reports {@link #STATUS_AP_LISTENING}. On UNO Q the
     * MCU cannot create an access point: Linux owns Wi-Fi, so the hotspot must already be running
     * ({@code nmcli device wifi hotspot ssid <ssid> password <password>}); this method only
     * initializes the MCU-to-Linux bridge, ignores both arguments, and {@link #status} reports
     * {@link #STATUS_CONNECTED} once the bridge is up.
     */
    public static native void beginAP(String ssid, String password);

    public static native int status();

    /**
     * Writes the board's current IPv4 address into {@code octets} (caller-owned, at least 4
     * entries), one byte value (0-255) per element, most significant first — e.g. {@code 192, 168,
     * 1, 45}. Only meaningful once {@link #status} reports {@link #STATUS_CONNECTED}; UNO Q's
     * current router API does not expose this address and writes four zeroes. Juno has no
     * heap, so there is no {@code String}-returning form here — print each octet with {@link
     * io.github.jabrena.juno.api.io.usb.Serial#print(int)} and a literal {@code "."} between them.
     */
    public static native void localIP(int[] octets);
}
