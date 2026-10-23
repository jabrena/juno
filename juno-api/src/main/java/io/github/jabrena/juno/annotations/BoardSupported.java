package io.github.jabrena.juno.annotations;

/** A type token for an Arduino board {@link Board @Board} can select as a compilation target. */
public sealed interface BoardSupported permits
        ArduinoUnoR3,
        ArduinoUnoR4WiFi,
        ArduinoUnoQ,
        ArduinoUnoR3WiFiRev2,
        ArduinoNanoEsp32,
        M5StackCardputer {
}
