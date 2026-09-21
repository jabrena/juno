/**
 * Code generation: turns optimized Juno IR into a complete Arduino sketch for the Cortex-M4
 * (RA4M1) UNO R4.
 *
 * <p>{@link io.github.jabrena.juno.backend.CortexM4AsmBackend} is Juno's sole backend. It emits
 * GNU ARM (Cortex-M4, Thumb-2) assembly directly from an {@link io.github.jabrena.juno.ir.IrProgram},
 * plus a small {@code extern "C"} C++ runtime shim (GPIO/Serial/LED matrix/Wi-Fi/HTTP/JSON
 * helpers, the fixed-capacity arena allocator and its mark/sweep collector, and {@code long}/
 * {@code float}/{@code double} support) that the generated assembly calls into and that the
 * Renesas toolchain compiles and links alongside it. Its {@code Output} record bundles the
 * generated assembly text, the shim source, and the entry-point symbol the {@code .ino} wrapper
 * calls from {@code setup()}.
 */
package io.github.jabrena.juno.backend;
