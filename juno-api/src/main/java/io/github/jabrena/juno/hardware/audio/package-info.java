/**
 * The board's audio output. {@link io.github.jabrena.juno.hardware.audio.Audio} plays tones on the Cardputer's built-in
 * speaker; take it once at startup and reuse it, because tone playback allocates no Java objects.
 */
@org.jspecify.annotations.NullMarked
package io.github.jabrena.juno.hardware.audio;
