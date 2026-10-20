/**
 * The Java-facing hardware API that Juno's compiler recognizes as intrinsics.
 *
 * <p>Every operation here is declared as ordinary Java — a {@code native} method, or a plain
 * static method built only from other supported operations — so a program using this API still
 * compiles and reads like normal Java. At link time, Juno's {@code linker}/{@code backend}
 * recognize the {@code native} methods (see {@code IntrinsicRegistry}) and emit their Arduino
 * C++/assembly implementation directly, instead of requiring a JVM to interpret them. There is no
 * runtime reflection, dependency injection, or driver discovery: the mapping from a Java call to
 * generated hardware code is fixed and closed-world.
 *
 * <p>The API lives in subpackages, grouped by the hardware or concern they address:
 * <ul>
 *   <li>{@code hardware} — the board's own peripherals, handed out by
 *       {@link io.github.jabrena.juno.api.hardware.MCU}: general-purpose I/O ({@code hardware.gpio},
 *       {@link io.github.jabrena.juno.api.hardware.gpio.Gpio}, {@link io.github.jabrena.juno.api.hardware.gpio.DigitalOutput}),
 *       I2C ({@code hardware.i2c}), serial port I/O ({@code hardware.serial}), USB HID mouse control
 *       ({@code hardware.hid}), the UNO R4 WiFi's built-in LED matrix with its frame/canvas helpers and
 *       bitmap fonts ({@code hardware.led}), and program-wide introspection: arena and flash usage
 *       ({@link io.github.jabrena.juno.api.hardware.BoardMemory}) and the board the program was compiled for
 *       ({@link io.github.jabrena.juno.api.hardware.BoardModel}).</li>
 *   <li>{@code net} — the Wi-Fi/UDP/HTTP/JSON/email networking stack ({@code net.http},
 *       {@code net.email}) and the
 *       {@link io.github.jabrena.juno.api.net.InternetClock} configuration for the internet-backed
 *       {@link java.time.LocalDateTime#now()} subset.</li>
 *   <li>{@code thirdparty} — shields, sensors and devices from other vendors: {@code grove}
 *       (the passive Grove Base Shield pin map and allocation-free Grove sensor drivers), {@code lcd}
 *       (character LCD shields), {@code tft} (the ELEGOO 2.8" TFT touch shield and its parallel bus),
 *       {@code imu}, {@code servo}, {@code ir}, {@code sd} and {@code lego} (Powered Up, RCX, Scout and
 *       Power Functions).</li>
 *   <li>{@code time} — the monotonic uptime clock ({@link io.github.jabrena.juno.api.time.Clock}),
 *       for measuring elapsed time without blocking.</li>
 *   <li>{@code util} — general-purpose utilities: blocking millisecond/microsecond delays
 *       ({@link io.github.jabrena.juno.api.util.Delay}), seeded pseudorandom numbers
 *       ({@link io.github.jabrena.juno.api.util.Random}) and UUID byte conversion
 *       ({@link io.github.jabrena.juno.api.util.UuidCodec}).</li>
 *   <li>{@code mvc} — views, navigation and a frame-loop controller for multi-screen TFT programs.</li>
 * </ul>
 *
 * <p>Classes in this package and its subpackages are final, non-instantiable utility types (or,
 * for a handle like {@code DigitalOutput}, a zero-cost wrapper around a primitive pin number) with
 * no shared mutable state of their own: Juno has no heap for general object graphs, so the API is
 * deliberately kept close to the primitive operations the generated firmware actually performs.
 *
 * <p>An entry-point class selects its compilation target with a {@link
 * io.github.jabrena.juno.annotations.Board @Board} annotation and then calls into this API from
 * {@code main}.
 */
@NullMarked
package io.github.jabrena.juno.api;

import org.jspecify.annotations.NullMarked;
