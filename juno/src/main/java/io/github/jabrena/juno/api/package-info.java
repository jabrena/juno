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
 * <p>This package holds simple, cross-cutting operations directly:
 * <ul>
 *   <li>{@link io.github.jabrena.juno.api.Delay} — blocking millisecond/microsecond delays.</li>
 *   <li>{@link io.github.jabrena.juno.api.Clock} — the monotonic uptime clock, for measuring
 *       elapsed time without blocking.</li>
 * </ul>
 *
 * <p>Board-specific or peripheral-specific operations live in subpackages instead, grouped by the
 * hardware they address:
 * <ul>
 *   <li>{@code io} — general-purpose I/O
 *       ({@link io.github.jabrena.juno.api.io.Gpio}, {@link io.github.jabrena.juno.api.io.DigitalOutput}),
 *       USB serial ({@code io.usb}), USB HID mouse control ({@code io.hid}), and the Wi-Fi/HTTP/JSON
 *       networking stack ({@code io.net}).</li>
 *   <li>{@code led} — the UNO R4 WiFi's built-in LED matrix, its frame/canvas helpers, and bitmap
 *       fonts.</li>
 *   <li>{@code lcd} — drivers for character LCD shields, built entirely from {@code io} and
 *       {@link io.github.jabrena.juno.api.Delay} rather than a new compiler intrinsic.</li>
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
package io.github.jabrena.juno.api;
