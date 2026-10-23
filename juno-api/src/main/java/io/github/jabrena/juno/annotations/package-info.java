/**
 * Compile-time-only annotations an entry-point class uses to configure Juno's compilation, read
 * directly from the class file rather than at runtime.
 *
 * <ul>
 *   <li>{@link io.github.jabrena.juno.annotations.Board @Board} selects the target Arduino board,
 *       e.g. {@code @Board(ArduinoOneR4WiFiBoard.class)}. A class with no {@code @Board} annotation
 *       must receive its target from the build request. {@link io.github.jabrena.juno.annotations.BoardSupported}
 *       is the sealed type token {@code @Board} accepts, implemented by
 *       {@link io.github.jabrena.juno.annotations.ArduinoOneR3Board} (its classic 8-bit ATmega328P, under Arduino's AVR
 *       core), {@link io.github.jabrena.juno.annotations.ArduinoOneR4WiFiBoard},
 *       {@link io.github.jabrena.juno.annotations.ArduinoOneQBoard} (its STM32U585 microcontroller,
 *       under Arduino's Zephyr core), {@link io.github.jabrena.juno.annotations.ArduinoOneR3WiFiRev2Board}
 *       (its 8-bit ATmega4809, under Arduino's megaAVR core) and
 *       {@link io.github.jabrena.juno.annotations.ArduinoNanoEsp32Board} (its Xtensa ESP32-S3, under Arduino's ESP32
 *       core); the linker resolves the selection into a
 *       {@code io.github.jabrena.juno.board.Board} compilation target.</li>
 *   <li>{@link io.github.jabrena.juno.annotations.Watchdog @Watchdog} enables the hardware watchdog
 *       on UNO R3, UNO WiFi Rev2 and UNO R4 WiFi, rebooting the board if generated code ever stops
 *       kicking it (including on a {@code juno_panic()}) instead of hanging forever. UNO Q's current
 *       Arduino Zephyr firmware does not enable its watchdog subsystem.</li>
 * </ul>
 */
@NullMarked
package io.github.jabrena.juno.annotations;

import org.jspecify.annotations.NullMarked;
