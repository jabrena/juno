/**
 * The C++ runtime shim the generated assembly links against: the arena allocator and collector, Serial, threads, and
 * every optional library binding a program's {@link io.github.jabrena.juno.backend.runtime.spi.ShimFeature}s call for.
 *
 * <p>The C++ itself lives in {@code src/main/resources/runtime} (see
 * {@link io.github.jabrena.juno.backend.runtime.RuntimeSource}); the classes here choose which fragments a program
 * needs and fill in their placeholders. Anything that differs between Arduino cores comes from
 * {@link io.github.jabrena.juno.backend.runtime.spi.CoreRuntime}.
 */
@NullMarked
package io.github.jabrena.juno.backend.runtime;

import org.jspecify.annotations.NullMarked;
