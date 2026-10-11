package io.github.jabrena.juno.games.gladiator;

import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/** Screenshot-only entry point covering both Solo result views. */
public final class SoloResultScreenshot {
    private SoloResultScreenshot() {
    }

    public static void main(String[] args) {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        FighterProfile player = new FighterProfile(FighterProfile.SPARTACUS, Opening.MURMILLO, Brain.AGGRESSIVE);
        FighterProfile opponent = new FighterProfile(FighterProfile.CRIXUS, Opening.THRAEX, Brain.AMBUSH);
        SoloPresentation.showResult(true, player, opponent);
        SoloPresentation.showResult(false, player, opponent);
        while (true) {
            Delay.millis(1000);
        }
    }
}
