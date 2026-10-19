/**
 * The built-in LED matrix of the UNO R4 WiFi (12x8) and the UNO Q (13x8):
 * {@link io.github.jabrena.juno.api.led.LedMatrix}, the raw {@code begin}/{@code loadFrame}/{@code clear}
 * operations that {@link io.github.jabrena.juno.api.hardware.MCU#getLedMatrix()} hands out, and
 * {@link io.github.jabrena.juno.api.led.LedCanvas}, a {@code boolean[HEIGHT][MAX_WIDTH]} pixel buffer
 * with shape and text drawing helpers that render into it and send it to the board's matrix with
 * {@code show}.
 *
 * <p>{@link io.github.jabrena.juno.api.led.LedMatrixFont} (5x7 digits/uppercase),
 * {@link io.github.jabrena.juno.api.led.LedMatrixFontAscii} (5x7, full printable ASCII), and
 * {@link io.github.jabrena.juno.api.led.LedMatrixFontSmall} (a compact 3x5 digit font) are bitmap
 * fonts for drawing text one glyph at a time onto an {@code LedCanvas} frame — Juno v0.1 has
 * neither arrays of pre-built glyph data nor a {@code String}-aware "print" entry point here, so
 * each font instead encodes a glyph as a small function returning whether one pixel is lit, and
 * only the glyphs a program actually calls are linked into the generated firmware.
 */
@NullMarked
package io.github.jabrena.juno.api.led;

import org.jspecify.annotations.NullMarked;
