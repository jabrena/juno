package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.net.Wifi;

/**
 * The board's {@link Wifi}: each method is a Wi-Fi intrinsic the compiler lowers to the board's networking library,
 * so it has no Java body and cannot run, or be mocked, on a desktop JVM. Mock {@link Wifi} instead.
 */
public final class NativeWifi implements Wifi {
    /** The board's Wi-Fi radio (onboard, or the UNO R3's Arduino WiFi Shield): the only instance. */
    public static final NativeWifi INSTANCE;

    /**
     * What {@link io.github.jabrena.juno.hardware.ArduinoUno#getWifi()},
     * {@link io.github.jabrena.juno.hardware.M5StackCardputer#getWifi()}, and
     * {@link io.github.jabrena.juno.hardware.device.wifi.ArduinoWifiShield#getWifi()} return: the board's radio,
     * unless a whole-program emulator in a test replaced it.
     */
    private static Wifi wifi;

    static {
        INSTANCE = new NativeWifi();
        wifi = INSTANCE;
    }

    private NativeWifi() {
    }

    /** The radio the board facade hands out. */
    public static Wifi wifi() {
        return wifi;
    }

    /**
     * Makes the board facade hand out {@code replacement}: for a
     * whole-program emulator in a test, never on the board.
     */
    public static void setWifi(Wifi replacement) {
        wifi = replacement;
    }

    @Override
    public native void begin(String ssid, String password);

    @Override
    public native void beginAP(String ssid, String password);

    @Override
    public native int status();

    @Override
    public native void localIP(int[] octets);

    @Override
    public native void gatewayIP(int[] octets);

    @Override
    public native void macAddress(int[] octets);
}
