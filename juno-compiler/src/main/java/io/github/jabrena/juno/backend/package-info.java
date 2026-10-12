/**
 * Code generation: turns optimized Juno IR into a complete Arduino sketch, generated assembly plus the C++ runtime shim
 * it links against, for every supported board.
 *
 * <p>This package holds what both code generators share: frame and program layout, the calling convention and
 * {@link io.github.jabrena.juno.backend.IntrinsicLowering}. {@link io.github.jabrena.juno.backend.thumb2} generates
 * GNU ARM Thumb-2 assembly for the UNO R4 WiFi and the UNO Q, {@link io.github.jabrena.juno.backend.avr} AVR assembly
 * for the UNO WiFi Rev2. Both assemble the shim through {@link io.github.jabrena.juno.backend.runtime}, and reach a
 * board's {@link io.github.jabrena.juno.board.ArduinoCore} only through
 * {@link io.github.jabrena.juno.backend.runtime.spi.CoreRuntime}.
 *
 * <p>Dependencies point one way: the code generators depend on the runtime, the per-core implementations depend on the
 * shared runtime, and both depend on the contracts in {@code runtime.spi}, which depend on nothing else in the backend.
 */
@NullMarked
package io.github.jabrena.juno.backend;

import org.jspecify.annotations.NullMarked;
