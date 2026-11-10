package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.internal.NativeGpio;
import io.github.jabrena.juno.hardware.internal.NativeWifi;
import io.github.jabrena.juno.hardware.net.Wifi;

/**
 * The Raspberry Pi Pico 2 W's board features. Its built-in LED sits on the CYW43439 radio chip rather than an RP2350
 * pin; {@link Gpio#getBuiltinLed()} returns the number the board's core routes to it.
 */
public final class RaspberryPico2 {

    private RaspberryPico2() {
    }

    /** The Raspberry Pi Pico 2 W's GPIO pins. */
    public static Gpio getGpio() {
        return NativeGpio.gpio();
    }

    /** The Raspberry Pi Pico 2 W's onboard CYW43439 Wi-Fi radio, in station or access-point mode. */
    public static Wifi getWifi() {
        return NativeWifi.wifi();
    }
}
