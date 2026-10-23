package io.github.jabrena.juno.annotations;

/** A type token for an Arduino board {@link Board @Board} can select as a compilation target. */
public sealed interface BoardSupported permits
        ArduinoUnoR3Board,
        ArduinoUnoR4WiFiBoard,
        ArduinoUnoQBoard,
        ArduinoUnoR3WiFiRev2Board,
        ArduinoNanoEsp32Board,
        M5StackCardputerBoard {
}
