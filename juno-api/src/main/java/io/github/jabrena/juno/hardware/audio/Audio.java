package io.github.jabrena.juno.hardware.audio;

/** The Cardputer's built-in speaker. Take it once at startup and reuse it; tone playback allocates no Java objects. */
public interface Audio {
    /** Sets the speaker master volume, clamped to the M5Stack range from {@code 0} (mute) to {@code 255} (maximum). */
    void setVolume(int volume);

    /** Stops the currently playing tone, if any. */
    void stop();

    /** Plays a tone at {@code frequencyHz} for {@code durationMillis}. */
    void tone(int frequencyHz, int durationMillis);
}
