package io.github.jabrena.juno.api.net;

/** Test double shadowing the {@code juno} artifact's native {@code Wifi}: always connected, at 192.168.1.2. */
public final class Wifi {
    public static final int STATUS_CONNECTED = 3;
    public static final int STATUS_AP_LISTENING = 7;

    private Wifi() {
    }

    public static void begin(String ssid, String password) {
    }

    public static void beginAP(String ssid, String password) {
    }

    public static int status() {
        return STATUS_CONNECTED;
    }

    public static void localIP(int[] octets) {
        octets[0] = 192;
        octets[1] = 168;
        octets[2] = 1;
        octets[3] = 2;
    }
}
