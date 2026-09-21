/**
 * The UNO R4 WiFi's built-in 12x8 LED matrix: {@link io.github.jabrena.juno.api.led.LedMatrix},
 * the raw compiler-intrinsic {@code begin}/{@code loadFrame}/{@code clear} operations, and
 * {@link io.github.jabrena.juno.api.led.LedCanvas}, a {@code boolean[HEIGHT][WIDTH]} pixel buffer
 * with shape and text drawing helpers that render into it and send it to the hardware with
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
package io.github.jabrena.juno.api.led;
