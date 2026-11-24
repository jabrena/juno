/**
 * On-board language model: {@link io.github.jabrena.juno.ai.LanguageModel} runs a JLM1 character-level
 * transformer, Juno's model format that {@code juno-lm} trains, from PSRAM. It is taken from
 * {@link io.github.jabrena.juno.hardware.MCU#getLanguageModel()} on the boards with PSRAM.
 */
@NullMarked
package io.github.jabrena.juno.ai;

import org.jspecify.annotations.NullMarked;
