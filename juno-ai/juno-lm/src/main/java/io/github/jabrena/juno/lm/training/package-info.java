/**
 * Full-precision training of a JLM1 network on the build machine, with hand-written backpropagation, Adam and
 * one worker thread per core, and its quantization into a {@link io.github.jabrena.juno.lm.JlmModel}.
 */
@NullMarked
package io.github.jabrena.juno.lm.training;

import org.jspecify.annotations.NullMarked;
