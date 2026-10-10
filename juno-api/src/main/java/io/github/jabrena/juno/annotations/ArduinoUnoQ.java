package io.github.jabrena.juno.annotations;

/**
 * Selects the Arduino UNO Q as {@link Board @Board}'s target: Juno programs run on its STM32U585
 * microcontroller (Cortex-M33) under Arduino's Zephyr core. Its LED matrix and Wi-Fi belong to the
 * board's Linux side, so the {@code LedMatrix} and network intrinsics are not available.
 */
public final class ArduinoUnoQ implements ArduinoBoard {
    private ArduinoUnoQ() {
    }
}
