package io.github.jabrena.juno.hardware.net;

import io.github.jabrena.juno.hardware.internal.NativeWifi;

/**
 * The board's Wi-Fi radio for whole-program tests: a {@link Wifi} that {@code ArduinoUno.getWifi()} and
 * {@code ArduinoWifiShield.getWifi()} return once {@link #install()} has run. It is always connected, at 192.168.1.2.
 */
public final class EmulatedWifi implements Wifi {
    private static final EmulatedWifi INSTANCE = new EmulatedWifi();
    private static final int[] MAC = {0x02, 0x00, 0x5e, 0x10, 0x00, 0x02};

    private EmulatedWifi() {
    }

    /** Makes {@code ArduinoUno.getWifi()} from now on hand out this emulated radio instead of the board's. */
    public static void install() {
        NativeWifi.setWifi(INSTANCE);
    }

    @Override
    public void begin(String ssid, String password) {
    }

    @Override
    public void beginAP(String ssid, String password) {
    }

    @Override
    public int status() {
        return STATUS_CONNECTED;
    }

    @Override
    public void localIP(int[] octets) {
        octets[0] = 192;
        octets[1] = 168;
        octets[2] = 1;
        octets[3] = 2;
    }

    @Override
    public void gatewayIP(int[] octets) {
        octets[0] = 192;
        octets[1] = 168;
        octets[2] = 1;
        octets[3] = 1;
    }

    @Override
    public void macAddress(int[] octets) {
        System.arraycopy(MAC, 0, octets, 0, MAC.length);
    }
}
