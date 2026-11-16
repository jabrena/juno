/**
 * Board-side implementations of {@code java.net} classes. The compiler reads every class here under {@code java.net}
 * instead of {@code io.github.jabrena.juno.jdk.net}, so a program written against the JDK's {@code java.net.URI} runs
 * this code on the board. Each class covers a documented subset of its JDK counterpart.
 */
@NullMarked
package io.github.jabrena.juno.jdk.net;

import org.jspecify.annotations.NullMarked;
