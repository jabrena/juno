package io.github.jabrena.juno.board;

/**
 * A physical board family. UNO generation is one family-specific classification; boards from other manufacturers
 * deliberately use their own family enum instead of being assigned a fabricated UNO generation.
 */
public sealed interface BoardFamily permits UnoGeneration, NanoFamily, Esp32Family, M5StackFamily, PicoFamily, MbedFamily {
}
