package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.device.camera.Camera;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.internal.NativeBluetoothLe;
import io.github.jabrena.juno.hardware.internal.NativeCamera;
import io.github.jabrena.juno.hardware.internal.NativeClock;
import io.github.jabrena.juno.hardware.internal.NativeGpio;
import io.github.jabrena.juno.hardware.internal.NativeI2c;
import io.github.jabrena.juno.hardware.internal.NativeSdCard;
import io.github.jabrena.juno.hardware.internal.NativeWifi;
import io.github.jabrena.juno.hardware.net.BluetoothLe;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.time.Clock;
import org.jspecify.annotations.Nullable;

/**
 * The AI Thinker ESP32-CAM's board features: GPIO, onboard Wi-Fi, its built-in microSD socket, and its OV2640 camera.
 */
public final class Esp32Cam {
    private static final int SD_CHIP_SELECT = 13;

    private static @Nullable SdCard sdCard;

    private Esp32Cam() {
    }

    /** The AI Thinker ESP32-CAM's GPIO pins. */
    public static Gpio getGpio() {
        return NativeGpio.gpio();
    }

    /** The AI Thinker ESP32-CAM's onboard Wi-Fi radio. */
    public static Wifi getWifi() {
        return NativeWifi.wifi();
    }

    /** The AI Thinker ESP32-CAM's millisecond and microsecond clock, the same object on every call. */
    public static Clock getClock() {
        return NativeClock.clock();
    }

    /** The AI Thinker ESP32-CAM's OV2640 camera, which delivers JPEG frames into a caller-owned buffer. */
    public static Camera getCamera() {
        return NativeCamera.camera();
    }

    /**
     * The card in the AI Thinker ESP32-CAM's microSD socket. Its {@link SdCard#begin()} selects GPIO13; the runtime
     * configures the socket's SPI pins (SCK 14, MISO 2 and MOSI 15). The same instance is returned every call.
     */
    public static SdCard getSdCard() {
        SdCard card = sdCard;
        if (card == null) {
            card = NativeSdCard.sdCard(SD_CHIP_SELECT);
            sdCard = card;
        }
        return card;
    }

    /**
     * The AI Thinker ESP32-CAM's I2C bus on GPIO26 (SDA) and GPIO27 (SCL), the camera's SCCB pins. An external breakout
     * can share the bus with the OV2640 at its own address. The same instance is returned every call.
     */
    public static I2c getI2c() {
        return NativeI2c.i2c();
    }

    /** The AI Thinker ESP32-CAM's Bluetooth LE radio. */
    public static BluetoothLe getBluetoothLe() {
        return NativeBluetoothLe.bluetoothLe();
    }
}
