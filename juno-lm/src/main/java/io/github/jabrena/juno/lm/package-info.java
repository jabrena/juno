/**
 * JLM1, Juno's language-model format: a character-level transformer whose weights are signed bytes with one
 * {@code float} scale per row, behind a header that declares its dimensions, context and alphabet. The network
 * follows the design of WollyMonjac's TINY 2.1 (MIT), whose TNY2 files {@link io.github.jabrena.juno.lm.Tny2Import}
 * reads. {@link io.github.jabrena.juno.lm.JlmReader} and
 * {@link io.github.jabrena.juno.lm.JlmWriter} read and write the binary file, and
 * {@link io.github.jabrena.juno.lm.JlmInference} runs it exactly as the board engine does.
 */
@NullMarked
package io.github.jabrena.juno.lm;

import org.jspecify.annotations.NullMarked;
