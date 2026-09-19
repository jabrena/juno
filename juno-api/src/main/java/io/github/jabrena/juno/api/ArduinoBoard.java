package io.github.jabrena.juno.api;

/** A type token for an Arduino board {@link Board @Board} can select as a compilation target. */
public sealed interface ArduinoBoard permits ArduinoUnoR4WiFi, ArduinoUnoR4Minima {
}
