package io.github.jabrena.juno.hardware.net;

/**
 * The board's Bluetooth LE radio. It exists only on the boards with a BLE adapter: UNO R4 WiFi and UNO Q
 * ({@link io.github.jabrena.juno.hardware.ArduinoUno#getBluetoothLe()}), Nano ESP32, AI Thinker ESP32-CAM and M5Stack
 * Cardputer. The UNO R3 and UNO WiFi Rev2 have none, so the compiler rejects those accessor calls. Get it once, at
 * startup, and pass it to the protocols built on it, such as
 * {@link io.github.jabrena.juno.remote.lego.poweredup.RemotePoweredUpHub}.
 *
 * <p>The interface carries no operations yet: a protocol that needs the radio takes it as proof the board has one.
 */
public interface BluetoothLe {
}
