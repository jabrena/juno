/**
 * Closed-world reachability: starting at {@code main}, follows every statically resolvable call,
 * resolves hardware intrinsics, and rejects anything reachable that falls
 * outside Juno's supported subset — before any code generation is attempted.
 *
 * <p>{@link io.github.jabrena.juno.linker.Linker} performs the reachability walk and produces a
 * {@link io.github.jabrena.juno.linker.Program}: the entry point, every reachable
 * {@link io.github.jabrena.juno.linker.LinkedMethod}, every loaded class (needed so lowering can
 * resolve fields on classes never themselves called into), the resolved
 * {@link io.github.jabrena.juno.board.Board} target, and an optional watchdog timeout.
 * {@link io.github.jabrena.juno.linker.Descriptor} parses JVM method/field descriptor strings.
 *
 * <p>Unreachable methods are simply omitted from {@code Program}, which is how Juno keeps
 * generated firmware limited to what a program actually calls.
 */
@NullMarked
package io.github.jabrena.juno.linker;

import org.jspecify.annotations.NullMarked;
