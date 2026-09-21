/**
 * Compile-time-only annotations an entry-point class uses to configure Juno's compilation, read
 * directly from the class file rather than at runtime.
 *
 * <ul>
 *   <li>{@link io.github.jabrena.juno.annotations.Board @Board} selects the target Arduino board,
 *       e.g. {@code @Board(ArduinoUnoR4WiFi.class)}. A class with no {@code @Board} annotation
 *       targets the UNO R4 WiFi by default. {@link io.github.jabrena.juno.annotations.ArduinoBoard}
 *       is the sealed type token {@code @Board} accepts, currently implemented only by
 *       {@link io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi}; the linker resolves the
 *       selection into a {@code io.github.jabrena.juno.board.Board} compilation target.</li>
 *   <li>{@link io.github.jabrena.juno.annotations.Watchdog @Watchdog} enables the RA4M1's hardware
 *       watchdog timer, rebooting the board if generated code ever stops kicking it (including on
 *       a {@code juno_panic()}) instead of hanging forever.</li>
 * </ul>
 */
package io.github.jabrena.juno.annotations;
