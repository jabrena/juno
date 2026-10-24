package io.github.jabrena.juno.games.doom.map;

import io.github.jabrena.juno.hardware.serial.Serial;

/**
 * Loads the requested map with its {@link MapReader}. Doom runs on the main thread alone (the engine leaves no RAM on
 * the UNO Q for thread stacks), so a view requests a map, draws its screen, and then {@link #step()} holds the frame
 * while the reader loads the map and draws its own progress bar, as the original game does.
 */
public final class DoomMapLoader {
    public static final int IDLE = 0;
    public static final int LOADING = 1;
    public static final int READY = 2;
    public static final int FAILED = 3;

    private final MapReader reader;
    private int status = IDLE;
    private int episode;
    private int map;
    private int skill;

    public DoomMapLoader(MapReader reader) {
        this.reader = reader;
    }

    /** Requests a map and shows its progress bar at ({@code x}, {@code y}); the next {@link #step()} reads it. */
    public void begin(int selectedEpisode, int selectedMap, int selectedSkill, int x, int y, int width) {
        episode = selectedEpisode;
        map = selectedMap;
        skill = selectedSkill;
        status = LOADING;
        reader.showProgress(x, y, width);
    }

    /** Reads the requested map, holding the frame until it is ready or has failed; does nothing otherwise. */
    public void step() {
        if (status != LOADING) {
            return;
        }
        Serial.print("Doom: loading E");
        Serial.print(episode);
        Serial.print("M");
        Serial.println(map);
        boolean loaded = reader.load(episode, map, skill);
        status = loaded ? READY : FAILED;
        Serial.print(loaded ? "Doom: map ready E" : "Doom: map load failed E");
        Serial.print(episode);
        Serial.print("M");
        Serial.println(map);
    }

    /** Forgets a request that has not been read yet. */
    public void cancel() {
        status = IDLE;
    }

    public int status() {
        return status;
    }
}
