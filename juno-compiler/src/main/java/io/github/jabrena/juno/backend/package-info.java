/**
 * Code generation: turns optimized Juno IR into a complete Arduino sketch for any supported board
 * (the Cortex-M4 UNO R4 WiFi and the Cortex-M33 UNO Q).
 *
 * <p>{@link io.github.jabrena.juno.backend.Thumb2AsmBackend} is Juno's sole backend. It emits
 * GNU ARM Thumb-2 assembly directly from an {@link io.github.jabrena.juno.ir.IrProgram},
 * plus a small {@code extern "C"} C++ runtime shim (GPIO/Serial/LED matrix/Wi-Fi/HTTP/JSON
 * helpers, the fixed-capacity arena allocator and its mark/sweep collector, and {@code long}/
 * {@code float}/{@code double} support) that the generated assembly calls into and that the
 * board's Arduino toolchain compiles and links alongside it. Per-core glue (delay/yield) comes from
 * the sealed {@code CoreRuntime}, chosen once from the board's
 * {@link io.github.jabrena.juno.board.ArduinoCore}. Its {@code Output} record bundles the
 * generated assembly text, the shim source, and the entry-point symbol the {@code .ino} wrapper
 * calls from {@code setup()}.
 */
package io.github.jabrena.juno.backend;
