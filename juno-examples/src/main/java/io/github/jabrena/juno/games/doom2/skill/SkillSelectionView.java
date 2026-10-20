package io.github.jabrena.juno.games.doom2.skill;

import io.github.jabrena.juno.api.hardware.serial.Serial;
import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.api.util.mvc.Navigator;
import io.github.jabrena.juno.api.util.mvc.View;
import io.github.jabrena.juno.games.doom2.commons.ChoiceList;
import io.github.jabrena.juno.games.doom2.commons.Doom2State;
import io.github.jabrena.juno.games.doom2.commons.Doom2Views;
import io.github.jabrena.juno.games.doom2.commons.Screen;

/** DOOM's skill menu, after the episode: which monsters and pickups each map places, and how hard they hit. */
public final class SkillSelectionView implements View {
    private final Navigator navigator;
    private final Doom2State model;
    private final ChoiceList list = new ChoiceList(42, 28, Doom2State.SKILLS);

    public SkillSelectionView(Navigator navigator, Doom2State model) {
        this.navigator = navigator;
        this.model = model;
    }

    @Override
    public void enter() {
        TftTouchShield.fillScreen(Screen.BACKGROUND);
        Screen.showCentered("CHOOSE SKILL", 8, 3, TftTouchShield.RED);
        Screen.drawBack();
        drawSkills();
        Serial.println("Doom2: skill view active");
    }

    @Override
    public void render() {
        // A static menu: everything is drawn on entry and on a choice.
    }

    @Override
    public void touch(int x, int y) {
        if (Screen.isBack(x, y)) {
            Serial.println("Doom2: returning to episode view");
            navigator.back();
            return;
        }
        int row = list.rowAt(x, y);
        if (row >= 0) {
            model.chooseSkill(row + 1);
            drawSkills();
            Serial.print("Doom2: skill selected ");
            Serial.println(row + 1);
            navigator.show(Doom2Views.MAP_LOADING);
        }
    }

    private void drawSkills() {
        for (int skill = 1; skill <= Doom2State.SKILLS; skill++) {
            list.draw(skill - 1, name(skill), detail(skill), true, model.skill() == skill);
        }
    }

    private static String name(int skill) {
        return switch (skill) {
            case 1 -> "I'M TOO YOUNG TO DIE";
            case 2 -> "HEY, NOT TOO ROUGH";
            case 3 -> "HURT ME PLENTY";
            case 4 -> "ULTRA-VIOLENCE";
            default -> "NIGHTMARE!";
        };
    }

    private static String detail(int skill) {
        return switch (skill) {
            case 1 -> "Few monsters, every hit halved";
            case 2 -> "Few monsters, full damage";
            case 3 -> "More monsters, full damage";
            case 4 -> "Every monster, full damage";
            default -> "Every monster, and faster";
        };
    }
}
