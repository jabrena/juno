package io.github.jabrena.juno.annotations;

/**
 * Selects the Arduino Nano ESP32 as {@link Board @Board}'s target: Juno programs run on its ESP32-S3, a dual-core
 * Xtensa LX7, under Arduino's {@code arduino:esp32} core. Support starts with the basics: {@code Gpio},
 * {@code DigitalOutput}, {@code Delay} and {@code Serial} over the native USB port, with pins numbered as printed on
 * the board ({@code D13} is the built-in LED). Its Wi-Fi and Bluetooth radios are not reachable from Juno yet.
 */
public final class ArduinoNanoEsp32 implements ArduinoBoard {
    private ArduinoNanoEsp32() {
    }
}
