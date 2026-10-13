package io.github.jabrena.juno.games.doom2.death;

import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.io.serial.Serial;
import io.github.jabrena.juno.api.mvc.Navigator;
import io.github.jabrena.juno.api.mvc.View;
import io.github.jabrena.juno.api.tft.TftTouchShield;
import io.github.jabrena.juno.games.doom2.commons.Doom2Views;
import io.github.jabrena.juno.games.doom2.commons.Screen;

/**
 * DOOM's death card over the last frame of the view. For two seconds it only says YOU DIED, so a finger still firing
 * cannot skip it; then a blinking prompt asks for the tap that returns to the cover and a new game.
 */
public final class DeathView implements View {
    private static final int HOLD_MILLIS = 2000;
    private static final int BLINK_FRAMES = 21;
    private static final int VISIBLE_FRAMES = 13;
    private static final int PLAQUE_X = 14;
    private static final int PLAQUE_Y = 84;
    private static final int PLAQUE_WIDTH = 292;
    private static final int PLAQUE_HEIGHT = 72;
    private static final int PROMPT_Y = 136;

    private final Navigator navigator;
    private int enteredAt;
    private int frame;

    public DeathView(Navigator navigator) {
        this.navigator = navigator;
    }

    @Override
    public void enter() {
        Screen.plaque(PLAQUE_X, PLAQUE_Y, PLAQUE_WIDTH, PLAQUE_HEIGHT);
        Screen.showCentered("YOU DIED", PLAQUE_Y + 12, 4, TftTouchShield.RED);
        enteredAt = Clock.millis();
        frame = 0;
        Serial.println("Doom2: death view active");
    }

    @Override
    public void render() {
        if (!held()) {
            return;
        }
        int phase = frame % BLINK_FRAMES;
        if (phase == 0 || phase == VISIBLE_FRAMES) {
            Screen.showCentered(phase == 0 ? "TAP TO RETURN TO TITLE" : "                      ", PROMPT_Y, 1,
                    TftTouchShield.YELLOW);
        }
        frame = frame + 1;
    }

    @Override
    public void touch(int x, int y) {
        if (held()) {
            Serial.println("Doom2: returning to cover");
            navigator.show(Doom2Views.COVER);
        }
    }

    private boolean held() {
        return Clock.millis() - enteredAt >= HOLD_MILLIS;
    }

}
