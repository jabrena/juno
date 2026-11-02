package io.github.jabrena.juno.games.lunarlander;

import io.github.jabrena.juno.annotations.ArduinoUnoR4WiFiBoard;
import io.github.jabrena.juno.annotations.Board;
import io.github.jabrena.juno.hardware.ArduinoUno;
import io.github.jabrena.juno.hardware.device.tft.TftTouchShield;
import io.github.jabrena.juno.hardware.time.Clock;
import io.github.jabrena.juno.util.Delay;
import io.github.jabrena.juno.util.Random;

/**
 * Lunar Lander for the ELEGOO TFT shield. Land upright and slowly on a marked pad before fuel runs
 * out. The package separates flight physics, terrain, rendering, controls, and interludes.
 */
@Board(ArduinoUnoR4WiFiBoard.class)
public final class LunarLander {
    private static final Clock CLOCK = ArduinoUno.getClock();
    private static final int START_FUEL = 1500;
    private static final int CPU_START_FUEL = 3000;
    private static final int CRASH_PENALTY = 250;

    private LunarLander() {
    }

    public static void main(String[] args) {
        TftTouchShield tft = new TftTouchShield();
        short[] ground = new short[Terrain.WIDTH];
        byte[] pads = new byte[Terrain.WIDTH];
        tft.begin();
        tft.setRotation(TftTouchShield.PORTRAIT_FLIPPED);
        while (true) {
            Interludes.cover(tft);
            Controls.waitForTap(tft);
            Controls.choosePilot(tft);
            Random.seed(CLOCK.micros());
            Flight.newGame(Controls.autopilot ? CPU_START_FUEL : START_FUEL);
            while (Flight.fuel > 0) {
                Flight.descent = Flight.descent + 1;
                finishDescent(tft, Flight.fly(tft, ground, pads), ground, pads);
            }
            Flight.best = Math.max(Flight.best, Flight.score);
            Hud.drawHeader(tft);
            Interludes.outOfFuel(tft);
        }
    }

    private static void finishDescent(TftTouchShield tft, int multiplier, short[] ground, byte[] pads) {
        if (multiplier > 0) {
            Flight.score = Flight.score + 50 * multiplier;
            Interludes.landed(tft, 50 * multiplier);
        } else {
            LanderRenderer.explode(tft, ground, pads);
            Flight.fuel = Math.max(0, Flight.fuel - CRASH_PENALTY);
            Interludes.crashed(tft);
        }
        Hud.drawHeader(tft);
        Delay.millis(1500);
    }
}
