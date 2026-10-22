package io.github.jabrena.juno.annotations;

/**
 * Selects the M5Stack Cardputer as {@link Board @Board}'s target. The initial Juno support covers
 * USB serial output through {@code M5.begin()} and {@code M5.println(String)}.
 */
public final class M5Cardputer implements ArduinoBoard {
    private M5Cardputer() {
    }
}
