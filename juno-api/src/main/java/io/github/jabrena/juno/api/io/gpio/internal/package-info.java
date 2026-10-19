/**
 * The GPIO package's native implementation: {@link io.github.jabrena.juno.api.io.gpio.internal.NativeDigitalOutput}
 * holds the {@code native} factory behind {@code DigitalOutput.of}, which Juno lowers to Arduino core calls. A native
 * method cannot run on a desktop JVM.
 */
package io.github.jabrena.juno.api.io.gpio.internal;
