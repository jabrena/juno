/**
 * The board's native implementations: the classes whose methods are compiler intrinsics. Each one holds the
 * {@code native} methods that Juno lowers to Arduino core calls, and the public types in the other API packages
 * (such as {@code Gpio} and {@code Clock}) forward to them. A native method cannot run on a desktop JVM.
 */
@org.jspecify.annotations.NullMarked
package io.github.jabrena.juno.hardware.internal;
