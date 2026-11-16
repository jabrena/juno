/**
 * Board-side implementations of {@code java.util} classes. The compiler reads every class here under
 * {@code java.util} instead of {@code io.github.jabrena.juno.jdk.util}, so a program written against the JDK's
 * {@code Optional} or {@code OptionalLong} runs this code on the board. Each class covers a documented subset of its
 * JDK counterpart.
 */
@NullMarked
package io.github.jabrena.juno.jdk.util;

import org.jspecify.annotations.NullMarked;
