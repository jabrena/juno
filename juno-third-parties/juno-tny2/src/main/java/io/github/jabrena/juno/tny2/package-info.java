/**
 * The TNY2 model format of TINY 2.1: a character-level transformer whose weights are signed bytes with one
 * {@code float} scale per row. {@link io.github.jabrena.juno.tny2.Tny2Reader} and
 * {@link io.github.jabrena.juno.tny2.Tny2Writer} read and write the binary file, and
 * {@link io.github.jabrena.juno.tny2.Tny2Inference} runs it exactly as the board engine does.
 */
@NullMarked
package io.github.jabrena.juno.tny2;

import org.jspecify.annotations.NullMarked;
