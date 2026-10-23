package io.github.jabrena.juno.hardware.internal;

import io.github.jabrena.juno.hardware.audio.Audio;

/** The M5Stack Cardputer's {@link Audio}, implemented by a compiler intrinsic over M5Cardputer.Speaker. */
public final class NativeAudio implements Audio {
    public static final NativeAudio INSTANCE = new NativeAudio();

    private static Audio audio = INSTANCE;

    private NativeAudio() {
    }

    /** The audio device the M5StackCardputer facade hands out. */
    public static Audio audio() {
        return audio;
    }

    /** Replaces the device for a whole-program emulator in a test, never on the board. */
    public static void setAudio(Audio replacement) {
        audio = replacement;
    }

    @Override
    public native void setVolume(int volume);

    @Override
    public native void stop();

    @Override
    public native void tone(int frequencyHz, int durationMillis);
}
