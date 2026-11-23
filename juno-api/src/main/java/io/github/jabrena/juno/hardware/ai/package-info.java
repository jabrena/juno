/**
 * On-board language model: {@link io.github.jabrena.juno.hardware.ai.LanguageModel} runs a TNY2 character-level
 * transformer, the format of TINY 2.1 that {@code juno-third-parties/juno-tny2} trains, from PSRAM. It is taken from
 * {@link io.github.jabrena.juno.hardware.MCU#getLanguageModel()} on the boards with PSRAM.
 */
@NullMarked
package io.github.jabrena.juno.hardware.ai;

import org.jspecify.annotations.NullMarked;
