package io.github.jabrena.juno.games.doom2.gameplay;

import io.github.jabrena.juno.games.doom.DoomEngine;
import io.github.jabrena.juno.games.doom2.commons.Doom2State;
import io.github.jabrena.juno.games.doom2.commons.Doom2Views;
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
    private final Doom2State model;
    private final DoomEngine engine;

    public GameplayView(Navigator navigator, Doom2State model, DoomEngine engine) {
        this.navigator = navigator;
        this.model = model;
        this.engine = engine;
    }

    @Override
    public void enter() {
        engine.start(model.autopilot(), model.takeNewGame());
        Serial.println("Doom2: gameplay view active");
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
            Serial.println("Doom2: map finished");
            navigator.show(Doom2Views.REPORT);
        } else if (outcome == DoomEngine.DIED) {
            Serial.println("Doom2: marine died");
            navigator.show(Doom2Views.DEATH);
        } else {
            Serial.println("Doom2: quit to cover");
            navigator.show(Doom2Views.COVER);
        }
    }

    @Override
    public void touch(int x, int y) {
        // The engine polls the touch screen during its frame.
    }
}
