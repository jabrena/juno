package io.github.jabrena.juno.hardware;

import io.github.jabrena.juno.hardware.audio.Audio;
import io.github.jabrena.juno.hardware.device.camera.Camera;
import io.github.jabrena.juno.hardware.device.sd.SdCard;
import io.github.jabrena.juno.hardware.display.Display;
import io.github.jabrena.juno.hardware.gpio.Gpio;
import io.github.jabrena.juno.hardware.hid.Mouse;
import io.github.jabrena.juno.hardware.i2c.I2c;
import io.github.jabrena.juno.hardware.internal.NativeAudio;
import io.github.jabrena.juno.hardware.internal.NativeBattery;
import io.github.jabrena.juno.hardware.internal.NativeBluetoothLe;
import io.github.jabrena.juno.hardware.internal.NativeBoardMemory;
import io.github.jabrena.juno.hardware.internal.NativeBoardModel;
import io.github.jabrena.juno.hardware.internal.NativeCamera;
import io.github.jabrena.juno.hardware.internal.NativeClock;
import io.github.jabrena.juno.hardware.internal.NativeDisplay;
import io.github.jabrena.juno.hardware.internal.NativeGpio;
import io.github.jabrena.juno.hardware.internal.NativeI2c;
import io.github.jabrena.juno.hardware.internal.NativeKeyboard;
import io.github.jabrena.juno.hardware.internal.NativeLedMatrix;
import io.github.jabrena.juno.hardware.internal.NativeMouse;
import io.github.jabrena.juno.hardware.internal.NativeOnboardImu;
import io.github.jabrena.juno.hardware.internal.NativeOnboardSdCard;
import io.github.jabrena.juno.hardware.internal.NativeSpi;
import io.github.jabrena.juno.hardware.internal.NativeWifi;
import io.github.jabrena.juno.hardware.keyboard.Keyboard;
import io.github.jabrena.juno.hardware.led.LedMatrix;
import io.github.jabrena.juno.hardware.net.BluetoothLe;
import io.github.jabrena.juno.hardware.net.Wifi;
import io.github.jabrena.juno.hardware.power.Battery;
import io.github.jabrena.juno.hardware.spi.Spi;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.robotics.Accelerometer;
import io.github.jabrena.juno.robotics.Gyroscope;

/**
 * The selected board's onboard services. {@code @Board} or the build's board option chooses the target; reachable
 * getters that the declared boards cannot provide fail at compile time. Each getter returns a shared service and
 * allocates nothing per call. Get services once at startup and pass their interfaces to application objects so tests
 * can substitute mocks.
 */
public final class MCU {
    private MCU() {
    }

    /** The selected board's pins. */
    public static Gpio getGpio() {
        return NativeGpio.gpio();
    }

    /** The selected board's primary I2C bus. */
    public static I2c getI2c() {
        return NativeI2c.i2c();
    }

    /** The selected board's primary SPI bus. */
    public static Spi getSpi() {
        return NativeSpi.spi();
    }

    /** The selected board acting as a USB HID mouse. */
    public static Mouse getMouse() {
        return NativeMouse.mouse();
    }

    /** The selected board's onboard LED matrix. */
    public static LedMatrix getLedMatrix() {
        return NativeLedMatrix.ledMatrix();
    }

    /** The selected board's onboard Wi-Fi service. */
    public static Wifi getWifi() {
        return NativeWifi.wifi();
    }

    /** The selected board's Bluetooth LE service. */
    public static BluetoothLe getBluetoothLe() {
        return NativeBluetoothLe.bluetoothLe();
    }

    /** The selected board's monotonic clock. */
    public static Clock getClock() {
        return NativeClock.clock();
    }

    /** Which board the program was compiled for. */
    public static BoardModel getBoardModel() {
        return NativeBoardModel.boardModel();
    }

    /** Juno's arena usage and the program's flash footprint on the selected board. */
    public static BoardMemory getBoardMemory() {
        return NativeBoardMemory.boardMemory();
    }

    /** The selected board's onboard accelerometer. */
    public static Accelerometer getAccelerometer() {
        return NativeOnboardImu.accelerometer();
    }

    /** The selected board's onboard gyroscope. */
    public static Gyroscope getGyroscope() {
        return NativeOnboardImu.gyroscope();
    }

    /** The selected board's onboard camera. */
    public static Camera getCamera() {
        return NativeCamera.camera();
    }

    /** The card in the selected board's onboard SD socket. */
    public static SdCard getSdCard() {
        return NativeOnboardSdCard.sdCard();
    }

    /** The selected board's onboard display. */
    public static Display getDisplay() {
        return NativeDisplay.display();
    }

    /** The selected board's onboard keyboard. */
    public static Keyboard getKeyboard() {
        return NativeKeyboard.keyboard();
    }

    /** The selected board's onboard speaker. */
    public static Audio getAudio() {
        return NativeAudio.audio();
    }

    /** The selected board's onboard battery monitor. */
    public static Battery getBattery() {
        return NativeBattery.battery();
    }
}
