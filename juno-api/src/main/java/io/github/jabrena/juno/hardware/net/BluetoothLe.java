package io.github.jabrena.juno.hardware.net;

/**
 * The board's Bluetooth LE radio, as a central: it scans for a device that advertises a service, connects to it, and
 * exchanges messages with one characteristic of that service. It knows no protocol; a protocol built on it, such as
 * {@link io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub}, frames its own messages.
 *
 * <p>It exists only on the boards with a BLE adapter: UNO R4 WiFi and UNO Q
 * ({@link io.github.jabrena.juno.hardware.MCU#getBluetoothLe()}), Nano ESP32, AI Thinker ESP32-CAM, M5Stack
 * Cardputer and Raspberry Pi Pico 2 W. The UNO R3 and UNO WiFi Rev2 have none, so the compiler rejects those accessor
 * calls. Get it once, at startup, and pass it to the protocols built on it.
 *
 * <p>The radio holds one connection at a time. Its messages are copied through caller-owned byte arrays, so the calls
 * allocate nothing.
 */
public interface BluetoothLe {
    /**
     * Scans for the first device advertising {@code serviceUuid}, connects to it, and subscribes to notifications on
     * {@code characteristicUuid}. Waits at most {@code timeoutMillis}, or indefinitely when it is zero or negative.
     * Copies up to {@code capacity} bytes of the device's manufacturer data into {@code advertisement}.
     *
     * @return the manufacturer data's length, once connected; {@code -1} when no device connected in time
     */
    int connect(String serviceUuid, String characteristicUuid, int timeoutMillis, byte[] advertisement, int capacity);

    /** Whether a device connected by {@link #connect} is still connected. */
    boolean isConnected();

    /** Writes the first {@code length} bytes of {@code message} to the connected device's characteristic. */
    void write(byte[] message, int length);

    /**
     * Copies the next notification from the connected device into {@code buffer}, up to {@code capacity} bytes, and
     * returns its length. Returns {@code 0} when no notification is waiting; a notification is never empty.
     */
    int read(byte[] buffer, int capacity);

    /** Disconnects from the connected device. */
    void disconnect();
}
