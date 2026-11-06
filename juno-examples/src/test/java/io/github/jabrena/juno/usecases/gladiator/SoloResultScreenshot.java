package io.github.jabrena.juno.usecases.gladiator;

import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.usecases.gladiator.arena.GladiatorClass;
import io.github.jabrena.juno.usecases.gladiator.screen.FighterProfile;
import io.github.jabrena.juno.usecases.gladiator.screen.SoloPresentation;
import io.github.jabrena.juno.util.Delay;

/** Screenshot-only entry point covering both Solo result views. */
public final class SoloResultScreenshot {
    private SoloResultScreenshot() {
    }

    public static void main(String[] args) {
        TftTouchShield tft = new TftTouchShield();
        tft.setRotation(TftTouchShield.LANDSCAPE);
        FighterProfile player = new FighterProfile(FighterProfile.SPARTACUS, GladiatorClass.MURMILLO);
        FighterProfile opponent = new FighterProfile(FighterProfile.CRIXUS, GladiatorClass.THRAEX);
        SoloPresentation.showResult(tft, true, player, opponent);
        SoloPresentation.showResult(tft, false, player, opponent);
        while (true) {
            Delay.millis(1000);
        }
    }
}
