package io.github.jabrena.juno.games.doom.gameplay;

import io.github.jabrena.juno.games.doom.commons.DoomState;
import io.github.jabrena.juno.games.doom.commons.DoomViews;
import io.github.jabrena.juno.games.doom.engine.DoomEngine;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/**
 * Plays the loaded map with the original engine, one engine frame per controller frame. The engine reads the touch
 * screen itself (turning, walking, firing, weapon slots, the pilot switch and the pause menu), so this view only
 * routes the outcome: the exit leads to the report, a death to the death screen and QUIT from the pause menu to the
 * cover.
 */
public final class GameplayView implements View {
    private final Navigator navigator;
    private final DoomState model;
    private final DoomEngine engine;

    public GameplayView(Navigator navigator, DoomState model, DoomEngine engine) {
        this.navigator = navigator;
        this.model = model;
        this.engine = engine;
    }

    @Override
    public void enter() {
        engine.start(model.autopilot(), model.takeNewGame());
        Serial.println("Doom: gameplay view active");
    }

    @Override
    public void render() {
        int outcome = engine.frame();
        if (outcome == DoomEngine.PLAYING) {
            return;
        }
        // The pilot can be switched in game; the next map starts with the one the player left on.
        if (engine.autopilot()) {
            model.chooseCpu();
        } else {
            model.chooseHuman();
        }
        if (outcome == DoomEngine.WON) {
            Serial.println("Doom: map finished");
            navigator.show(DoomViews.REPORT);
        } else if (outcome == DoomEngine.DIED) {
            Serial.println("Doom: marine died");
            navigator.show(DoomViews.DEATH);
        } else {
            Serial.println("Doom: quit to cover");
            navigator.show(DoomViews.COVER);
        }
    }

    @Override
    public void touch(int x, int y) {
        // The engine polls the touch screen during its frame.
    }
}
