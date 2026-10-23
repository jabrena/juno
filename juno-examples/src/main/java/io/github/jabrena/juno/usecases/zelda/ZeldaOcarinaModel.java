package io.github.jabrena.juno.usecases.zelda;

import io.github.jabrena.juno.hardware.audio.Audio;
import io.github.jabrena.juno.usecases.zelda.theme.ZeldaSoundtrackThemes;

/** State shared by the Zelda views and the playback task. */
public final class ZeldaOcarinaModel {
    public static final int VOLUME_STEP = 16;
    public static final int VOLUME_MIN = 0;
    public static final int VOLUME_MAX = 255;
    public static final int PLAYING = 0;
    public static final int STOPPED = 1;
    public static final int FINISHED = 2;

    private static final int INITIAL_VOLUME = 160;
    private final Audio audio;
    private int volume = INITIAL_VOLUME;
    private int repeat;
    private int note;
    private int remainingMillis;
    private int selectedTheme;
    private int state = STOPPED;
    private boolean themeSelected;

    public ZeldaOcarinaModel(Audio audio) {
        this.audio = audio;
        audio.setVolume(volume);
    }

    public int volume() {
        return volume;
    }

    public boolean themeSelected() {
        return themeSelected;
    }

    public int selectedTheme() {
        return selectedTheme;
    }

    public String themeTitle() {
        return ZeldaSoundtrackThemes.title(selectedTheme);
    }

    public void selectTheme(int theme) {
        if (theme < 0 || theme >= ZeldaSoundtrackThemes.COUNT) {
            return;
        }
        selectedTheme = theme;
        themeSelected = true;
    }

    public int state() {
        return state;
    }

    public boolean isPlaying() {
        return state == PLAYING;
    }

    public void increaseVolume() {
        setVolume(volume + VOLUME_STEP);
    }

    public void decreaseVolume() {
        setVolume(volume - VOLUME_STEP);
    }

    public void startTheme() {
        repeat = 0;
        note = 0;
        state = PLAYING;
        playCurrentNote();
    }

    public void stopTheme() {
        audio.stop();
        state = STOPPED;
    }

    /** Advances playback by one frame and reports whether the theme just finished. */
    public boolean advance(int elapsedMillis) {
        if (!isPlaying()) {
            return false;
        }
        remainingMillis = remainingMillis - elapsedMillis;
        while (remainingMillis <= 0 && isPlaying()) {
            note = note + 1;
            if (note == ZeldaSoundtrackThemes.noteCount(selectedTheme)) {
                note = 0;
                repeat = repeat + 1;
                if (repeat == ZeldaSoundtrackThemes.repeatCount(selectedTheme)) {
                    audio.stop();
                    state = FINISHED;
                    return true;
                }
            }
            playCurrentNote();
        }
        return false;
    }

    private void setVolume(int requestedVolume) {
        volume = requestedVolume < VOLUME_MIN
                ? VOLUME_MIN
                : requestedVolume > VOLUME_MAX ? VOLUME_MAX : requestedVolume;
        audio.setVolume(volume);
    }

    private void playCurrentNote() {
        int frequency = ZeldaSoundtrackThemes.frequencyHz(selectedTheme, note);
        int duration = ZeldaSoundtrackThemes.toneMillis(selectedTheme, note);
        remainingMillis = ZeldaSoundtrackThemes.stepMillis(selectedTheme, note);
        if (frequency > 0) {
            audio.tone(frequency, duration);
        }
    }
}
