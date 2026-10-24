package io.github.jabrena.juno.games.doom.map;

import io.github.jabrena.juno.games.doom.engine.DoomEngine;

/** Reads maps into the original DOOM engine: its WAD parser fills the tables and its planner routes the CPU pilot. */
public final class EngineMapReader implements MapReader {
    private final DoomEngine engine;

    public EngineMapReader(DoomEngine engine) {
        this.engine = engine;
    }

    @Override
    public boolean load(int episode, int map, int skill) {
        return engine.load(episode, map, skill);
    }

    @Override
    public void showProgress(int x, int y, int width) {
        engine.showProgress(x, y, width);
    }
}
