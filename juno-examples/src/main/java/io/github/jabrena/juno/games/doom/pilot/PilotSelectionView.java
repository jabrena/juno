package io.github.jabrena.juno.games.doom.pilot;

import io.github.jabrena.juno.games.doom.commons.ChoiceList;
import io.github.jabrena.juno.games.doom.commons.DoomState;
import io.github.jabrena.juno.games.doom.commons.DoomViews;
import io.github.jabrena.juno.games.doom.commons.Screen;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.serial.Serial;
import io.github.jabrena.juno.util.mvc.Navigator;
import io.github.jabrena.juno.util.mvc.View;

/** Lets the player select the HUMAN or CPU pilot and return to the cover. */
public final class PilotSelectionView implements View {

    private final TftTouchShield tft;
    static final int HUMAN = 0;
    static final int CPU = 1;

    private static final int WALL_FAR = 0x2424;
    /** HUMAN and CPU as rows of the same menu widget the episode and skill views use. */
    static final ChoiceList PILOTS = new ChoiceList(96, 40, 2);

    private final Navigator navigator;
    private final DoomState model;

    public PilotSelectionView(TftTouchShield tft, Navigator navigator, DoomState model) {
        this.tft = tft;
        this.navigator = navigator;
        this.model = model;
    }

    @Override
    public void enter() {
        tft.fillScreen(Screen.BACKGROUND);
        Screen.drawBack(tft);
        Screen.showCentered(tft, "CHOOSE PILOT", 36, 3, TftTouchShield.RED);
        Screen.showCentered(tft, "Who walks the map?", 74, 1, TftTouchShield.WHITE);
        drawChoices(tft);
        Screen.showCentered(tft, "Tap a pilot", 188, 1, WALL_FAR);
        Serial.println("Doom: pilot view active");
    }

    @Override
    public void render() {
    }

    @Override
    public void touch(int x, int y) {
        if (Screen.isBack(x, y)) {
            Serial.println("Doom: returning to cover");
            navigator.back();
            return;
        }
        int choice = PILOTS.rowAt(x, y);
        if (choice == HUMAN) {
            model.chooseHuman();
            drawChoices(tft);
            Serial.println("Doom: pilot HUMAN");
            navigator.show(DoomViews.EPISODES);
        } else if (choice == CPU) {
            model.chooseCpu();
            drawChoices(tft);
            Serial.println("Doom: pilot CPU");
            navigator.show(DoomViews.EPISODES);
        }
    }

    private void drawChoices(TftTouchShield tft) {
        PILOTS.draw(tft, HUMAN, "HUMAN", "You walk and shoot", true, !model.autopilot());
        PILOTS.draw(tft, CPU, "CPU", "Autopilot plays", true, model.autopilot());
    }
}
