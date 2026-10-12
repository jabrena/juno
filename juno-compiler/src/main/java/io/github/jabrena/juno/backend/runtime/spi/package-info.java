/**
 * The contracts between the code generators and the runtime shim: {@link io.github.jabrena.juno.backend.runtime.spi.ShimFeature},
 * the optional shim parts generated code asks for, and {@link io.github.jabrena.juno.backend.runtime.spi.CoreRuntime},
 * which each Arduino core implements in {@code backend.runtime.core}. This package depends on nothing else in the
 * backend, so every other backend package can depend on it without forming a cycle.
 */
@NullMarked
package io.github.jabrena.juno.backend.runtime.spi;

import org.jspecify.annotations.NullMarked;
