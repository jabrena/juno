package io.github.jabrena.juno.games.lunarlander;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFi;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.api.Clock;
import io.github.jabrena.juno.api.Delay;
import io.github.jabrena.juno.api.Random;
import io.github.jabrena.juno.api.tft.TftTouchShield;

/**
 * Lunar Lander for the ELEGOO TFT shield. Land upright and slowly on a marked pad before fuel runs
 * out. The package separates flight physics, terrain, rendering, controls, and interludes.
 */
@Board(ArduinoUnoR4WiFi.class)
public final class LunarLander {
    private static final int START_FUEL = 1500;
    private static final int CPU_START_FUEL = 3000;
    private static final int CRASH_PENALTY = 250;

    private LunarLander() {
    }

    public static void main(String[] args) {
        short[] ground = new short[Terrain.WIDTH];
        byte[] pads = new byte[Terrain.WIDTH];
        TftTouchShield.begin();
        TftTouchShield.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        while (true) {
            Interludes.cover();
            Controls.waitForTap();
            Controls.choosePilot();
            Random.seed(Clock.micros());
            Flight.newGame(Controls.autopilot ? CPU_START_FUEL : START_FUEL);
            while (Flight.fuel > 0) {
                Flight.descent = Flight.descent + 1;
                finishDescent(Flight.fly(ground, pads), ground, pads);
            }
            Flight.best = Math.max(Flight.best, Flight.score);
            Hud.drawHeader();
            Interludes.outOfFuel();
        }
    }

    private static void finishDescent(int multiplier, short[] ground, byte[] pads) {
        if (multiplier > 0) {
            Flight.score = Flight.score + 50 * multiplier;
            Interludes.landed(50 * multiplier);
        } else {
            LanderRenderer.explode(ground, pads);
            Flight.fuel = Math.max(0, Flight.fuel - CRASH_PENALTY);
            Interludes.crashed();
        }
        Hud.drawHeader();
        Delay.millis(1500);
    }
}
