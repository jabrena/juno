/**
 * Board-side programs the plugin ships and uploads itself, so a goal works from any project without a program of its
 * own: {@link io.github.jabrena.juno.maven.provisioner.SecretProvisioner}, which {@code juno:secret} uploads when no
 * {@code -Djuno.main} is given.
 *
 * <p>Unlike the rest of the plugin, these classes never run on the build machine: Juno compiles them for the board.
 * They follow the board-side rules — final classes over {@code juno-api} and the JDK, buffers allocated once and never
 * inside the main loop — and depend on nothing else in the plugin, since the goal copies only their class files onto
 * the compile classpath.
 */
@NullMarked
package io.github.jabrena.juno.maven.provisioner;

import org.jspecify.annotations.NullMarked;
