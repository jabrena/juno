package io.github.jabrena.juno.annotations;

/**
 * Selects the Arduino UNO WiFi Rev2 as {@link Board @Board}'s target: Juno programs run on its ATmega4809, an 8-bit
 * megaAVR (6 KB SRAM, 48 KB flash), under Arduino's {@code arduino:megaavr} core, with Wi-Fi through the u-blox
 * NINA-W102 module and the {@code WiFiNINA} library. Java's {@code int}, {@code long}, {@code float} and
 * {@code double} keep their JVM widths and IEEE-754 semantics there (the runtime brings its own 64-bit
 * {@code double}, as the AVR toolchain's is 32-bit), and {@code java.math}'s {@code BigInteger}/{@code BigDecimal}
 * work as on the other boards, within its small arena, as do {@code StructuredTaskScope}, {@code ScopedValue} and locks,
 * with a 512-byte stack per forked task. It has no LED matrix, and the double-precision transcendental {@code Math}
 * functions are not available.
 */
public final class ArduinoUnoR3WiFiRev2Board implements BoardSupported {
    private ArduinoUnoR3WiFiRev2Board() {
    }
}
