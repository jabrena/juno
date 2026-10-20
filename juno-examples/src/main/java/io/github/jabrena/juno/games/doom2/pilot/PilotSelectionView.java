package io.github.jabrena.juno.games.doom2.pilot;

import io.github.jabrena.juno.api.hardware.serial.Serial;
import io.github.jabrena.juno.api.mvc.Navigator;
import io.github.jabrena.juno.api.mvc.View;
import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.games.doom2.commons.ChoiceList;
import io.github.jabrena.juno.games.doom2.commons.Doom2State;
import io.github.jabrena.juno.games.doom2.commons.Doom2Views;
import io.github.jabrena.juno.games.doom2.commons.Screen;

/** Lets the player select the HUMAN or CPU pilot and return to the cover. */
public final class PilotSelectionView implements View {
    static final int HUMAN = 0;
    static final int CPU = 1;

    private static final int WALL_FAR = 0x2424;
    /** HUMAN and CPU as rows of the same menu widget the episode and skill views use. */
    static final ChoiceList PILOTS = new ChoiceList(96, 40, 2);

    private final Navigator navigator;
    private final Doom2State model;

    public PilotSelectionView(Navigator navigator, Doom2State model) {
        this.navigator = navigator;
        this.model = model;
    }

    @Override
    public void enter() {
        TftTouchShield.fillScreen(Screen.BACKGROUND);
        Screen.drawBack();
        Screen.showCentered("CHOOSE PILOT", 36, 3, TftTouchShield.RED);
        Screen.showCentered("Who walks the map?", 74, 1, TftTouchShield.WHITE);
        drawChoices();
        Screen.showCentered("Tap a pilot", 188, 1, WALL_FAR);
        Serial.println("Doom2: pilot view active");
    }

    @Override
    public void render() {
    }

    @Override
    public void touch(int x, int y) {
        if (Screen.isBack(x, y)) {
            Serial.println("Doom2: returning to cover");
            navigator.back();
            return;
        }
        int choice = PILOTS.rowAt(x, y);
        if (choice == HUMAN) {
            model.chooseHuman();
            drawChoices();
            Serial.println("Doom2: pilot HUMAN");
            navigator.show(Doom2Views.EPISODES);
        } else if (choice == CPU) {
            model.chooseCpu();
            drawChoices();
            Serial.println("Doom2: pilot CPU");
            navigator.show(Doom2Views.EPISODES);
        }
    }

    private void drawChoices() {
        PILOTS.draw(HUMAN, "HUMAN", "You walk and shoot", true, !model.autopilot());
        PILOTS.draw(CPU, "CPU", "Autopilot plays", true, model.autopilot());
    }
}
