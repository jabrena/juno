/**
 * The closed catalog of hardware operations Juno understands, independent of any particular
 * Java method signature or Arduino-side lowering.
 *
 * <p>{@link io.github.jabrena.juno.intrinsic.Intrinsic} enumerates every operation
 * (GPIO, Serial, the LED matrix, Wi-Fi/HTTP/JSON, HID, and so on) that the backend knows how to
 * emit. {@link io.github.jabrena.juno.intrinsic.IntrinsicRegistry} is the single place that maps a
 * Java {@link io.github.jabrena.juno.classfile.MethodRef} — an {@code io.github.jabrena.juno.api}
 * class's {@code native} method — to the {@link io.github.jabrena.juno.intrinsic.Intrinsic} it
 * implements, so the linker can resolve an API call to a concrete operation instead of rejecting
 * it as unreachable ordinary code.
 */
package io.github.jabrena.juno.intrinsic;
