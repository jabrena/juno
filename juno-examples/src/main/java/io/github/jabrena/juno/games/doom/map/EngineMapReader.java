package io.github.jabrena.juno.games.doom.map;

import io.github.jabrena.juno.games.doom.engine.DoomEngine;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;

/** Reads maps into the original DOOM engine: its WAD parser fills the tables and its planner routes the CPU pilot. */
public final class EngineMapReader implements MapReader {

    private final TftTouchShield tft;
    private final DoomEngine engine;

    public EngineMapReader(TftTouchShield tft, DoomEngine engine) {
        this.tft = tft;
        this.engine = engine;
    }

    @Override
    public boolean load(int episode, int map, int skill) {
        return engine.load(tft, episode, map, skill);
    }

    @Override
    public void showProgress(int x, int y, int width) {
        engine.showProgress(tft, x, y, width);
    }
}
