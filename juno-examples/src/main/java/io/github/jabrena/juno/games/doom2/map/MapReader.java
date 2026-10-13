package io.github.jabrena.juno.games.doom2.map;

/** Reads one map into the game engine, holding the caller until it ends; {@link DoomMapLoader} calls it. */
public interface MapReader {
    /** Draws the next {@link #load}'s progress bar at ({@code x}, {@code y}), {@code width} pixels long. */
    void showProgress(int x, int y, int width);

    /** Reads map EeMm at {@code skill} and prepares it to play; {@code false} when it is missing, broken or too large. */
    boolean load(int episode, int map, int skill);
}
