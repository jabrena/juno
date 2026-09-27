package io.github.jabrena.juno.api.io.net;

/**
 * WiFi connection control recognized as compiler intrinsics by Juno, backed by the Arduino
 * {@code WiFiS3} library. Requires {@code @Board(ArduinoUnoR4WiFi.class)} (the default board).
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

    public static native void begin(String ssid, String password);

    public static native int status();

    /**
     * Writes the board's current IPv4 address into {@code octets} (caller-owned, at least 4
     * entries), one byte value (0-255) per element, most significant first — e.g. {@code 192, 168,
     * 1, 45}. Only meaningful once {@link #status} reports {@link #STATUS_CONNECTED}; Juno has no
     * heap, so there is no {@code String}-returning form here — print each octet with {@link
     * io.github.jabrena.juno.api.io.usb.Serial#print(int)} and a literal {@code "."} between them.
     */
    public static native void localIP(int[] octets);
}
