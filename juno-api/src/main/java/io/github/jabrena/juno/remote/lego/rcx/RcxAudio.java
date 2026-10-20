package io.github.jabrena.juno.remote.lego.rcx;

/**
 * An RCX brick's speaker, from {@link RemoteRcx#getAudio()}. Each call returns whether the brick acknowledged, which
 * needs the IR receiver wired, without waiting for the sound to end.
 */
public final class RcxAudio {
    /** The six built-in sounds for {@link #playSound}. */
    public static final int SOUND_KEY_CLICK = 0;
    public static final int SOUND_BEEP = 1;
    public static final int SOUND_SWEEP_DOWN = 2;
    public static final int SOUND_SWEEP_UP = 3;
    public static final int SOUND_ERROR = 4;
    public static final int SOUND_FAST_SWEEP_UP = 5;

    private final RcxBrick brick;

    RcxAudio(RcxBrick brick) {
        this.brick = brick;
    }

    /** Plays one of the six built-in sounds, {@code SOUND_*}. */
    public boolean playSound(int sound) {
        return brick.playSound(sound);
    }

    /**
     * Plays a tone of {@code frequencyHz} (about 31 to 20000 Hz) for {@code durationCentiseconds} hundredths of a
     * second ({@code 1..255}). A tone replaces any sound still playing, so wait for the duration before the next.
     */
    public boolean playTone(int frequencyHz, int durationCentiseconds) {
        return brick.playTone(frequencyHz, durationCentiseconds);
    }
}
