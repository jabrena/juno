package io.github.jabrena.juno.games.gladiator;

import io.github.jabrena.juno.api.thirdparty.tft.TftTouchShield;
import io.github.jabrena.juno.api.util.Delay;
import io.github.jabrena.juno.games.gladiator.arena.GladiatorClass;
import io.github.jabrena.juno.games.gladiator.screen.FighterProfile;
import io.github.jabrena.juno.games.gladiator.screen.SoloPresentation;

/** Screenshot-only entry point covering both Solo result views. */
public final class SoloResultScreenshot {
    private SoloResultScreenshot() {
    }

    public static void main(String[] args) {
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.LANDSCAPE);
        FighterProfile player = new FighterProfile(FighterProfile.SPARTACUS, GladiatorClass.MURMILLO);
        FighterProfile opponent = new FighterProfile(FighterProfile.CRIXUS, GladiatorClass.THRAEX);
        SoloPresentation.showResult(true, player, opponent);
        SoloPresentation.showResult(false, player, opponent);
        while (true) {
            Delay.millis(1000);
        }
    }
}
